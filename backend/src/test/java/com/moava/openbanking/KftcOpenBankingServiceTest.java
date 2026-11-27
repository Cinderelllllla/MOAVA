package com.moava.openbanking;

import com.moava.member.mapper.MemberMapper;
import com.moava.member.service.JwtAuthenticationFilter;
import com.moava.member.service.JwtTokenProvider;
import com.moava.openbanking.client.OpenBankingProperties;
import com.moava.openbanking.dto.KftcAccountDto;
import com.moava.openbanking.dto.KftcOAuthDto;
import com.moava.openbanking.dto.KftcRecordDto;
import com.moava.openbanking.dto.KftcTransactionDto;
import com.moava.openbanking.dto.KftcTransferDto;
import com.moava.openbanking.exception.KftcOpenBankingException;
import com.moava.openbanking.exception.KftcResponseCode;
import com.moava.openbanking.mapper.KftcOpenBankingMapper;
import com.moava.openbanking.service.KftcOAuthService;
import com.moava.openbanking.service.KftcOpenBankingService;
import com.moava.openbanking.service.KftcSecurityGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KftcOpenBankingServiceTest {

    private static final String WD_FIN_NUM = "199000000112345678900099";
    private static final String LEADER_FIN_NUM = "199000000112345678900001"; // 의도적으로 WD보다 작은 값(정렬 락 검증)
    private static final String OTHER_USER_FIN_NUM = "199000000112345678900055";
    private static final String BANK_TRAN_ID = "M202600001U123456789";

    @Mock
    private KftcOpenBankingMapper mapper;

    private KftcSecurityGuard guard;
    private OpenBankingProperties properties;
    private KftcOAuthService oauthService;
    private KftcOpenBankingService openBankingService;

    @BeforeEach
    void setUp() {
        guard = new KftcSecurityGuard();
        properties = new OpenBankingProperties(
                "http://localhost:8081",
                "M202600001",
                "moava-demo-client",
                "moava-demo-secret"
        );
        oauthService = new KftcOAuthService(mapper, guard, properties);
        openBankingService = new KftcOpenBankingService(mapper, guard, properties);
    }

    // ==================== 1. OAuth 2.0 ====================

    @Test
    void oauth_authorizationCode_발급_토큰교환_검증_철회_전체흐름() {
        KftcOAuthDto.AuthorizeRequest authReq = new KftcOAuthDto.AuthorizeRequest();
        authReq.setClientId("moava-demo-client");
        authReq.setRedirectUri("http://localhost:5173/callback");
        authReq.setScope("login inquiry transfer");
        authReq.setState("csrf-state-1");
        authReq.setUserSeqNo("1000000001");

        KftcOAuthDto.AuthorizeResponse authRes = oauthService.issueAuthorizationCode(authReq, "127.0.0.1");
        assertEquals("A0000", authRes.getRspCode());
        assertNotNull(authRes.getCode());

        ArgumentCaptor<KftcRecordDto.OAuthCodeRecord> codeCaptor = ArgumentCaptor.forClass(KftcRecordDto.OAuthCodeRecord.class);
        verify(mapper).insertAuthCode(codeCaptor.capture());

        when(mapper.findAuthCode(authRes.getCode())).thenReturn(codeCaptor.getValue());
        when(mapper.consumeAuthCode(eq(authRes.getCode()), any(LocalDateTime.class))).thenReturn(1);

        KftcOAuthDto.TokenRequest tokenReq = new KftcOAuthDto.TokenRequest();
        tokenReq.setGrantType("authorization_code");
        tokenReq.setClientId("moava-demo-client");
        tokenReq.setClientSecret("moava-demo-secret");
        tokenReq.setCode(authRes.getCode());
        tokenReq.setRedirectUri("http://localhost:5173/callback");

        KftcOAuthDto.TokenResponse tokenRes = oauthService.issueToken(tokenReq, "127.0.0.1");
        assertEquals("A0000", tokenRes.getRspCode());
        assertEquals("Bearer", tokenRes.getTokenType());
        assertEquals("1000000001", tokenRes.getUserSeqNo());

        ArgumentCaptor<KftcRecordDto.OAuthTokenRecord> tokenCaptor = ArgumentCaptor.forClass(KftcRecordDto.OAuthTokenRecord.class);
        verify(mapper).insertOAuthToken(tokenCaptor.capture());
        // 원문 토큰이 아닌 SHA-256 해시가 저장되어야 한다
        assertNotEquals(tokenRes.getAccessToken(), tokenCaptor.getValue().getTokenHash());
        assertEquals(64, tokenCaptor.getValue().getTokenHash().length());

        when(mapper.findOAuthTokenByHash(tokenCaptor.getValue().getTokenHash())).thenReturn(tokenCaptor.getValue());
        KftcRecordDto.OAuthTokenRecord validated =
                oauthService.validateBearerToken("Bearer " + tokenRes.getAccessToken(), "transfer", "127.0.0.1");
        assertEquals("1000000001", validated.getUserSeqNo());

        when(mapper.revokeOAuthToken(tokenCaptor.getValue().getTokenHash())).thenReturn(1);
        KftcOAuthDto.RevokeResponse revokeRes = oauthService.revokeToken("Bearer " + tokenRes.getAccessToken(), "127.0.0.1");
        assertTrue(revokeRes.isRevoked());
    }

    @Test
    void oauth_clientCredentials_oob_발급과_비허용_scope_차단() {
        KftcOAuthDto.TokenRequest oobReq = new KftcOAuthDto.TokenRequest();
        oobReq.setGrantType("client_credentials");
        oobReq.setClientId("moava-demo-client");
        oobReq.setClientSecret("moava-demo-secret");
        oobReq.setScope("oob");

        KftcOAuthDto.TokenResponse res = oauthService.issueToken(oobReq, "127.0.0.1");
        assertEquals("A0000", res.getRspCode());
        assertEquals("oob", res.getScope());
        assertNull(res.getUserSeqNo());

        KftcOAuthDto.TokenRequest badScopeReq = new KftcOAuthDto.TokenRequest();
        badScopeReq.setGrantType("client_credentials");
        badScopeReq.setClientId("moava-demo-client");
        badScopeReq.setClientSecret("moava-demo-secret");
        badScopeReq.setScope("transfer");

        KftcOpenBankingException ex = assertThrows(
                KftcOpenBankingException.class,
                () -> oauthService.issueToken(badScopeReq, "127.0.0.1")
        );
        assertEquals(KftcResponseCode.INVALID_PARAMETER, ex.getResponseCode());
    }

    @Test
    void oauth_만료토큰_A0007_및_권한부족_A0008_차단() {
        KftcRecordDto.OAuthTokenRecord expired = new KftcRecordDto.OAuthTokenRecord();
        expired.setScope("inquiry");
        expired.setRevokedYn("N");
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(mapper.findOAuthTokenByHash(anyString())).thenReturn(expired);

        KftcOpenBankingException expiredEx = assertThrows(
                KftcOpenBankingException.class,
                () -> oauthService.validateBearerToken("Bearer expired-token", "inquiry", "127.0.0.1")
        );
        assertEquals(KftcResponseCode.UNAUTHORIZED_TOKEN, expiredEx.getResponseCode());

        KftcRecordDto.OAuthTokenRecord inquiryOnly = new KftcRecordDto.OAuthTokenRecord();
        inquiryOnly.setScope("inquiry");
        inquiryOnly.setRevokedYn("N");
        inquiryOnly.setExpiresAt(LocalDateTime.now().plusHours(1));
        when(mapper.findOAuthTokenByHash(anyString())).thenReturn(inquiryOnly);

        KftcOpenBankingException scopeEx = assertThrows(
                KftcOpenBankingException.class,
                () -> oauthService.validateBearerToken("Bearer valid-token", "transfer", "127.0.0.2")
        );
        assertEquals(KftcResponseCode.INSUFFICIENT_SCOPE, scopeEx.getResponseCode());
    }

    // ==================== 2. 실명 / 잔액 / 거래내역 조회 ====================

    @Test
    void 실명조회_정상_계좌없음_실명불일치_XSS차단() {
        KftcRecordDto.AccountRecord acc = account(WD_FIN_NUM, "088", "신한은행", "110-123-456789", "홍길동", "1000000001", 500_000L, "N");
        when(mapper.findAccountByBankAndNo("088", "110-123-456789")).thenReturn(acc);

        KftcAccountDto.RealNameRequest okReq = new KftcAccountDto.RealNameRequest();
        okReq.setBankCodeStd("088");
        okReq.setAccountNum("110-123-456789");
        okReq.setAccountHolderName("홍길동");

        KftcAccountDto.RealNameResponse res = openBankingService.inquireRealName(okReq, "127.0.0.1");
        assertEquals("A0000", res.getRspCode());
        assertTrue(res.isVerified());
        assertEquals(WD_FIN_NUM, res.getFintechUseNum());
        assertEquals(20, res.getBankTranId().length());

        // 실명 불일치 -> A0003
        KftcAccountDto.RealNameRequest wrongName = new KftcAccountDto.RealNameRequest();
        wrongName.setBankCodeStd("088");
        wrongName.setAccountNum("110-123-456789");
        wrongName.setAccountHolderName("김철수");
        assertEquals(KftcResponseCode.HOLDER_NAME_MISMATCH,
                assertThrows(KftcOpenBankingException.class,
                        () -> openBankingService.inquireRealName(wrongName, "127.0.0.1")).getResponseCode());

        // 계좌 없음 -> A0001
        when(mapper.findAccountByBankAndNo("088", "999-999-999999")).thenReturn(null);
        KftcAccountDto.RealNameRequest missing = new KftcAccountDto.RealNameRequest();
        missing.setBankCodeStd("088");
        missing.setAccountNum("999-999-999999");
        missing.setAccountHolderName("홍길동");
        assertEquals(KftcResponseCode.ACCOUNT_NOT_FOUND,
                assertThrows(KftcOpenBankingException.class,
                        () -> openBankingService.inquireRealName(missing, "127.0.0.1")).getResponseCode());

        // 예금주명 스크립트 주입 -> A0009
        KftcAccountDto.RealNameRequest xssReq = new KftcAccountDto.RealNameRequest();
        xssReq.setBankCodeStd("088");
        xssReq.setAccountNum("110-123-456789");
        xssReq.setAccountHolderName("<script>alert(1)</script>");
        assertEquals(KftcResponseCode.INVALID_PARAMETER,
                assertThrows(KftcOpenBankingException.class,
                        () -> openBankingService.inquireRealName(xssReq, "127.0.0.1")).getResponseCode());
    }

    @Test
    void 거래내역조회_타인계좌차단_및_XSS출력이스케이프() {
        KftcRecordDto.AccountRecord acc = account(WD_FIN_NUM, "088", "신한은행", "110-123-456789", "홍길동", "1000000001", 900_000L, "N");
        when(mapper.findAccountByFintechUseNum(WD_FIN_NUM)).thenReturn(acc);

        // 타 사용자 토큰으로 조회 시도 -> A0008
        KftcRecordDto.OAuthTokenRecord otherUserToken = token("inquiry", "9999999999");
        assertEquals(KftcResponseCode.INSUFFICIENT_SCOPE,
                assertThrows(KftcOpenBankingException.class,
                        () -> openBankingService.inquireBalance(WD_FIN_NUM, null, otherUserToken, "127.0.0.1")).getResponseCode());

        // 정상 사용자 토큰으로 거래내역 조회 + DB에 오염된 텍스트가 있어도 태그/스크립트는 제거하고 일반 특수문자는 이중 이스케이프하지 않음
        KftcRecordDto.TransactionRecord tx = new KftcRecordDto.TransactionRecord();
        tx.setBankTranId(BANK_TRAN_ID);
        tx.setFintechUseNum(WD_FIN_NUM);
        tx.setInoutType("출금");
        tx.setTranAmt(50_000L);
        tx.setAfterBalanceAmt(900_000L);
        tx.setPrintContent("<img src=x onerror=alert(1)>A&B's \"카페\"");
        tx.setCounterpartyName("김모임");
        tx.setBranchName("모아바모의점");
        tx.setTranAt(LocalDateTime.of(2026, 11, 24, 10, 30, 0));

        when(mapper.findTransactions(eq(WD_FIN_NUM), eq("출금"), any(), any(), eq("D"), eq(100)))
                .thenReturn(List.of(tx));

        KftcTransactionDto.TransactionListResponse res = openBankingService.inquireTransactionList(
                WD_FIN_NUM, "O", null, null, "D", null, null, token("inquiry", "1000000001"), "127.0.0.1");

        assertEquals("A0000", res.getRspCode());
        assertEquals(900_000L, res.getBalanceAmt());
        assertEquals(1, res.getPageRecordCnt());
        String sanitized = res.getResList().get(0).getPrintContent();
        assertFalse(sanitized.contains("<img"));
        assertFalse(sanitized.contains("&lt;"));
        assertFalse(sanitized.contains("&amp;"));
        assertEquals("A&B's \"카페\"", sanitized);
    }

    @Test
    void 거래내역조회_종료일_익일자정_미만조회_및_적요_90바이트초과_차단() {
        KftcRecordDto.AccountRecord acc = account(WD_FIN_NUM, "088", "신한은행", "110-123-456789", "홍길동", "1000000001", 900_000L, "N");
        when(mapper.findAccountByFintechUseNum(WD_FIN_NUM)).thenReturn(acc);
        when(mapper.findTransactions(eq(WD_FIN_NUM), isNull(), any(), any(), eq("D"), eq(100)))
                .thenReturn(List.of());

        openBankingService.inquireTransactionList(
                WD_FIN_NUM, "A", "20261101", "20261127", "D", null, null,
                token("inquiry", "1000000001"), "127.0.0.1"
        );

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).findTransactions(eq(WD_FIN_NUM), isNull(), fromCaptor.capture(), toCaptor.capture(), eq("D"), eq(100));
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0, 0), fromCaptor.getValue());
        assertEquals(LocalDateTime.of(2026, 11, 28, 0, 0, 0), toCaptor.getValue());

        // 한글 31자 = 93바이트(>90바이트 한도) -> INVALID_PARAMETER 차단
        KftcTransferDto.WithdrawRequest overByteReq = withdrawReq(WD_FIN_NUM, LEADER_FIN_NUM, 10_000L, BANK_TRAN_ID);
        overByteReq.setWdPrintContent("가".repeat(31));
        assertEquals(KftcResponseCode.INVALID_PARAMETER,
                assertThrows(KftcOpenBankingException.class,
                        () -> openBankingService.withdrawTransfer(overByteReq, token("oob", null), "127.0.0.1")).getResponseCode());
    }

    // ==================== 3. 출금이체 / 입금이체 원자성·락순서·예외 ====================

    @Test
    void 출금이체_오름차순_FOR_UPDATE_락순서_및_원자적_잔액_원장기록() {
        KftcRecordDto.AccountRecord wd = account(WD_FIN_NUM, "088", "신한은행", "110-123-456789", "홍길동", "1000000001", 200_000L, "N");
        KftcRecordDto.AccountRecord dps = account(LEADER_FIN_NUM, "004", "국민은행", "123401-04-987654", "김모임", "1000000099", 500_000L, "Y");

        when(mapper.countByBankTranId(BANK_TRAN_ID)).thenReturn(0);
        when(mapper.findAccountForUpdate(LEADER_FIN_NUM)).thenReturn(dps);
        when(mapper.findAccountForUpdate(WD_FIN_NUM)).thenReturn(wd);
        when(mapper.debitAccountBalance(eq(WD_FIN_NUM), eq(50_000L), any())).thenReturn(1);
        when(mapper.creditAccountBalance(eq(LEADER_FIN_NUM), eq(50_000L), any())).thenReturn(1);

        KftcTransferDto.WithdrawRequest req = new KftcTransferDto.WithdrawRequest();
        req.setBankTranId(BANK_TRAN_ID);
        req.setFintechUseNum(WD_FIN_NUM);
        req.setRecvFintechUseNum(LEADER_FIN_NUM);
        req.setTranAmt(50_000L);
        req.setWdPrintContent("11월 회비");
        req.setDpsPrintContent("홍길동 회비");

        KftcTransferDto.TransferResponse res =
                openBankingService.withdrawTransfer(req, token("transfer", "1000000001"), "127.0.0.1");

        assertEquals("A0000", res.getRspCode());
        assertEquals(BANK_TRAN_ID, res.getBankTranId());
        assertEquals(150_000L, res.getWdAfterBalanceAmt());
        assertEquals(550_000L, res.getDpsAfterBalanceAmt());

        // 데드락 방지 검증: WD_FIN_NUM > LEADER_FIN_NUM 이므로 반드시 LEADER_FIN_NUM 먼저 FOR UPDATE 호출
        InOrder inOrder = inOrder(mapper);
        inOrder.verify(mapper).findAccountForUpdate(LEADER_FIN_NUM);
        inOrder.verify(mapper).findAccountForUpdate(WD_FIN_NUM);
        inOrder.verify(mapper).debitAccountBalance(eq(WD_FIN_NUM), eq(50_000L), any());
        inOrder.verify(mapper).creditAccountBalance(eq(LEADER_FIN_NUM), eq(50_000L), any());
        inOrder.verify(mapper, times(2)).insertTransaction(any(KftcRecordDto.TransactionRecord.class));
    }

    @Test
    void 이체_필수예외_분기_전체검증() {
        // 1) 0원 이하 및 음수 금액 -> A0004
        for (Long badAmt : new Long[]{null, 0L, -10_000L, 200_000_000L}) {
            KftcTransferDto.WithdrawRequest req = withdrawReq(WD_FIN_NUM, LEADER_FIN_NUM, badAmt, BANK_TRAN_ID);
            assertEquals(KftcResponseCode.INVALID_AMOUNT,
                    assertThrows(KftcOpenBankingException.class,
                            () -> openBankingService.withdrawTransfer(req, token("oob", null), "127.0.0.1")).getResponseCode());
        }

        // 2) 동일 계좌 이체 -> A0011
        KftcTransferDto.WithdrawRequest sameReq = withdrawReq(WD_FIN_NUM, WD_FIN_NUM, 10_000L, BANK_TRAN_ID);
        assertEquals(KftcResponseCode.SAME_ACCOUNT_TRANSFER,
                assertThrows(KftcOpenBankingException.class,
                        () -> openBankingService.withdrawTransfer(sameReq, token("oob", null), "127.0.0.1")).getResponseCode());

        // 3) 중복 bank_tran_id 멱등성 차단 -> A0006
        when(mapper.countByBankTranId(BANK_TRAN_ID)).thenReturn(2);
        KftcTransferDto.WithdrawRequest dupReq = withdrawReq(WD_FIN_NUM, LEADER_FIN_NUM, 10_000L, BANK_TRAN_ID);
        assertEquals(KftcResponseCode.DUPLICATE_BANK_TRAN_ID,
                assertThrows(KftcOpenBankingException.class,
                        () -> openBankingService.withdrawTransfer(dupReq, token("oob", null), "127.0.0.1")).getResponseCode());

        // 4) 모임장 계좌(LEADER_YN='Y') 외 타 계좌 이체 차단 -> A0005
        when(mapper.countByBankTranId(BANK_TRAN_ID)).thenReturn(0);
        KftcRecordDto.AccountRecord wd = account(WD_FIN_NUM, "088", "신한은행", "110-123-456789", "홍길동", "1000000001", 200_000L, "N");
        KftcRecordDto.AccountRecord nonLeader = account(OTHER_USER_FIN_NUM, "004", "국민은행", "222-333-444444", "타인", "1000000055", 10_000L, "N");
        when(mapper.findAccountForUpdate(OTHER_USER_FIN_NUM)).thenReturn(nonLeader);
        when(mapper.findAccountForUpdate(WD_FIN_NUM)).thenReturn(wd);

        KftcTransferDto.WithdrawRequest nonLeaderReq = withdrawReq(WD_FIN_NUM, OTHER_USER_FIN_NUM, 10_000L, BANK_TRAN_ID);
        assertEquals(KftcResponseCode.UNAUTHORIZED_TARGET_ACCOUNT,
                assertThrows(KftcOpenBankingException.class,
                        () -> openBankingService.withdrawTransfer(nonLeaderReq, token("oob", null), "127.0.0.1")).getResponseCode());

        // 5) 잔액 부족 -> A0002
        KftcRecordDto.AccountRecord leader = account(LEADER_FIN_NUM, "004", "국민은행", "123401-04-987654", "김모임", "1000000099", 500_000L, "Y");
        when(mapper.findAccountForUpdate(LEADER_FIN_NUM)).thenReturn(leader);
        KftcTransferDto.WithdrawRequest overReq = withdrawReq(WD_FIN_NUM, LEADER_FIN_NUM, 999_000L, BANK_TRAN_ID);
        assertEquals(KftcResponseCode.INSUFFICIENT_BALANCE,
                assertThrows(KftcOpenBankingException.class,
                        () -> openBankingService.withdrawTransfer(overReq, token("oob", null), "127.0.0.1")).getResponseCode());

        // 6) 동시 경합 중 DB UNIQUE 위반(DuplicateKeyException) -> A0006 변환(트랜잭션 롤백 유도)
        when(mapper.debitAccountBalance(eq(WD_FIN_NUM), eq(10_000L), any())).thenReturn(1);
        when(mapper.creditAccountBalance(eq(LEADER_FIN_NUM), eq(10_000L), any())).thenReturn(1);
        doThrow(new DuplicateKeyException("UK_MOCK_KFTC_TX_IDEMPOTENT")).when(mapper).insertTransaction(any());

        KftcTransferDto.DepositRequest depReq = new KftcTransferDto.DepositRequest();
        depReq.setBankTranId(BANK_TRAN_ID);
        depReq.setWdFintechUseNum(WD_FIN_NUM);
        depReq.setFintechUseNum(LEADER_FIN_NUM);
        depReq.setTranAmt(10_000L);
        depReq.setPrintContent("입금이체 테스트");
        assertEquals(KftcResponseCode.DUPLICATE_BANK_TRAN_ID,
                assertThrows(KftcOpenBankingException.class,
                        () -> openBankingService.depositTransfer(depReq, token("oob", null), "127.0.0.1")).getResponseCode());
    }

    // ==================== 4. 공통 필터 & MyBatis XML 계약 검증 ====================

    @Test
    void jwtFilter는_오픈뱅킹_경로만_우회하고_기존_API는_유지한다() throws Exception {
        JwtTokenProvider provider = mock(JwtTokenProvider.class);
        MemberMapper memberMapper = mock(MemberMapper.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider, memberMapper);
        Method shouldNotFilter = JwtAuthenticationFilter.class.getDeclaredMethod("shouldNotFilter", jakarta.servlet.http.HttpServletRequest.class);
        shouldNotFilter.setAccessible(true);

        assertTrue((Boolean) shouldNotFilter.invoke(filter, new MockHttpServletRequest("POST", "/oauth/2.0/token")));
        assertTrue((Boolean) shouldNotFilter.invoke(filter, new MockHttpServletRequest("POST", "/v2.0/transfer/withdraw/fin_num")));
        assertFalse((Boolean) shouldNotFilter.invoke(filter, new MockHttpServletRequest("GET", "/api/member/me")));
    }

    @Test
    void mybatis_XML에_Mapper_모든_메서드가_정의되어_있다() throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("mapper/openbanking/KftcOpenBankingMapper.xml")) {
            assertNotNull(is, "KftcOpenBankingMapper.xml을 찾을 수 없습니다.");
            String xml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Pattern idPattern = Pattern.compile("<(?:select|insert|update|delete)\\s+id=\"([^\"]+)\"");
            Matcher matcher = idPattern.matcher(xml);
            java.util.Set<String> xmlIds = new java.util.HashSet<>();
            while (matcher.find()) {
                xmlIds.add(matcher.group(1));
            }
            for (Method method : KftcOpenBankingMapper.class.getDeclaredMethods()) {
                assertTrue(xmlIds.contains(method.getName()),
                        "XML에 누락된 Mapper 메서드: " + method.getName());
            }
        }
    }

    // ==================== 헬퍼 ====================

    private static KftcRecordDto.AccountRecord account(
            String finNum, String bankCode, String bankName, String accountNo,
            String holderName, String userSeqNo, long balance, String leaderYn
    ) {
        KftcRecordDto.AccountRecord acc = new KftcRecordDto.AccountRecord();
        acc.setFintechUseNum(finNum);
        acc.setBankCode(bankCode);
        acc.setBankName(bankName);
        acc.setAccountNo(accountNo);
        acc.setHolderName(holderName);
        acc.setUserSeqNo(userSeqNo);
        acc.setBalance(balance);
        acc.setLeaderYn(leaderYn);
        acc.setStatus("ACTIVE");
        return acc;
    }

    private static KftcRecordDto.OAuthTokenRecord token(String scope, String userSeqNo) {
        KftcRecordDto.OAuthTokenRecord tok = new KftcRecordDto.OAuthTokenRecord();
        tok.setScope(scope);
        tok.setUserSeqNo(userSeqNo);
        tok.setRevokedYn("N");
        tok.setExpiresAt(LocalDateTime.now().plusHours(1));
        return tok;
    }

    private static KftcTransferDto.WithdrawRequest withdrawReq(String wd, String dps, Long amt, String tranId) {
        KftcTransferDto.WithdrawRequest req = new KftcTransferDto.WithdrawRequest();
        req.setFintechUseNum(wd);
        req.setRecvFintechUseNum(dps);
        req.setTranAmt(amt);
        req.setBankTranId(tranId);
        return req;
    }
}
