package com.moava.openbanking;

import com.moava.crypto.service.CryptoKeyService;
import com.moava.openbanking.client.OpenBankingProperties;
import com.moava.openbanking.codef.CodefAccountMapper;
import com.moava.openbanking.codef.CodefBankCode;
import com.moava.openbanking.codef.CodefDto;
import com.moava.openbanking.codef.CodefException;
import com.moava.openbanking.dto.KftcOAuthDto;
import com.moava.openbanking.dto.MockBankDto;
import com.moava.openbanking.mapper.MockBankLedgerMapper;
import com.moava.openbanking.service.KftcOAuthService;
import com.moava.openbanking.service.KftcSecurityGuard;
import com.moava.openbanking.service.MockBankProvisionService;
import com.moava.openbanking.service.MockBankQuickConnectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MockBankQuickConnectServiceTest {

    @Mock
    private CodefAccountMapper codefAccountMapper;
    @Mock
    private MockBankLedgerMapper ledgerMapper;
    @Mock
    private CryptoKeyService cryptoKeyService;
    @Mock
    private KftcOAuthService kftcOAuthService;
    @Mock
    private MockBankProvisionService provisionService;

    private MockBankQuickConnectService service;

    @BeforeEach
    void setUp() {
        OpenBankingProperties props = new OpenBankingProperties(
                "http://localhost:8080",
                "M202600001",
                "moava-demo-client",
                "moava-demo-secret"
        );
        service = new MockBankQuickConnectService(
                codefAccountMapper,
                ledgerMapper,
                cryptoKeyService,
                kftcOAuthService,
                provisionService,
                new KftcSecurityGuard(),
                props
        );
    }

    @Test
    void 간편연결_시작_세션조회_재발송_및_4자리_1원인증_완료() {
        when(codefAccountMapper.findActiveMemberName(10L)).thenReturn("홍길동");
        when(provisionService.deriveUserSeqNo(10L)).thenReturn("1000000010");
        when(ledgerMapper.findAccountByBankAndDigits("088", "110123456789")).thenReturn(null);

        MockBankDto.QuickConnectStartRequest startReq = new MockBankDto.QuickConnectStartRequest();
        startReq.setOrganization("신한은행");
        startReq.setAccountNo("110-123-456789");
        startReq.setHolderName("홍길동");
        startReq.setPhone("010-1234-5678");

        MockBankDto.QuickConnectSessionResponse started = service.startQuickConnect(10L, startReq, "127.0.0.1");
        assertNotNull(started.getSessionToken());
        assertEquals("0088", started.getOrganization());
        assertEquals("신한은행", started.getBankName());
        assertEquals("****6789", started.getAccountMasked());
        assertEquals("010-****-5678", started.getPhoneMasked());
        assertTrue(started.getAuthCodeHint().matches("^[0-9]{4}$"));
        assertEquals("모아바" + started.getAuthCodeHint(), started.getDepositMemo());
        assertTrue(started.getBankAuthUrl().startsWith("/openbanking/bank-auth?session="));

        // 본인 세션 조회 성공, 타인 세션 조회 차단
        MockBankDto.QuickConnectSessionResponse fetched = service.getSession(10L, started.getSessionToken());
        assertEquals(started.getSessionToken(), fetched.getSessionToken());
        when(codefAccountMapper.findActiveMemberName(99L)).thenReturn("타인");
        assertThrows(CodefException.class, () -> service.getSession(99L, started.getSessionToken()));

        // 재발송 후 새 코드로 4자리 인증 완료
        MockBankDto.QuickConnectSessionResponse resent = service.resendAuthCode(10L, started.getSessionToken(), "127.0.0.1");
        String code = resent.getAuthCodeHint();

        KftcOAuthDto.AuthorizeResponse authzRes = new KftcOAuthDto.AuthorizeResponse();
        authzRes.setCode("kftc_code_test");
        when(kftcOAuthService.issueAuthorizationCode(any(), eq("127.0.0.1"))).thenReturn(authzRes);

        KftcOAuthDto.TokenResponse tokenRes = new KftcOAuthDto.TokenResponse();
        tokenRes.setScope("login inquiry transfer");
        when(kftcOAuthService.issueToken(any(), eq("127.0.0.1"))).thenReturn(tokenRes);

        MockBankDto.ProvisionResult provisionResult = new MockBankDto.ProvisionResult();
        provisionResult.setFintechUseNum("199088000010000000000001");
        provisionResult.setBalanceAmt(1_000_000L);
        when(provisionService.provisionFromCodef(
                eq(10L), eq(CodefBankCode.SHINHAN), eq("110123456789"), eq("홍길동"),
                eq(1_000_000L), anyList(), eq(1_000_000L), isNull(), eq(false)
        )).thenReturn(provisionResult);

        when(cryptoKeyService.getCurrentKeyVersion()).thenReturn(1);
        when(codefAccountMapper.findConnection(10L, "0088")).thenReturn(null);
        when(cryptoKeyService.encrypt("QUICK_OAUTH:0088", 1)).thenReturn("ENC_QUICK");
        when(cryptoKeyService.encrypt("110123456789", 1)).thenReturn("ENC_ACC");

        MockBankDto.QuickConnectVerifyRequest verifyReq = new MockBankDto.QuickConnectVerifyRequest();
        verifyReq.setAuthCode(code);
        verifyReq.setAgreed(true);

        MockBankDto.QuickConnectVerifyResponse verified =
                service.verifyAndComplete(10L, started.getSessionToken(), verifyReq, "127.0.0.1");

        assertTrue(verified.isVerified());
        assertEquals("0088", verified.getOrganization());
        assertEquals("login inquiry transfer", verified.getOauthScope());
        assertEquals("199088000010000000000001", verified.getMockAccount().getFintechUseNum());
        verify(codefAccountMapper).upsertConnection(any(CodefDto.ConnectionRecord.class));
        verify(codefAccountMapper).markAccountVerified(eq(10L), eq("0088"), eq("ENC_ACC"), eq("6789"), eq("홍길동"), eq(1), any());

        // 완료된 세션 재사용 차단
        assertThrows(CodefException.class, () -> service.verifyAndComplete(10L, started.getSessionToken(), verifyReq, "127.0.0.1"));
    }

    @Test
    void 소유주불일치_403차단_및_인증번호_오류_5회초과_세션만료() {
        when(codefAccountMapper.findActiveMemberName(10L)).thenReturn("홍길동");

        // 1) 소유주 실명 불일치 -> 403
        MockBankDto.QuickConnectStartRequest badHolder = new MockBankDto.QuickConnectStartRequest();
        badHolder.setOrganization("국민은행");
        badHolder.setAccountNo("123401-04-987654");
        badHolder.setHolderName("타인명의");
        badHolder.setPhone("010-1234-5678");
        CodefException holderEx = assertThrows(
                CodefException.class,
                () -> service.startQuickConnect(10L, badHolder, "127.0.0.1")
        );
        assertEquals("QUICK_CONNECT_HOLDER_MISMATCH", holderEx.getCode());
        assertEquals(403, holderEx.getHttpStatus().value());

        // 2) 정상 세션 발급 후 잘못된 4자리 번호 5회 입력 시 세션 폐기
        when(provisionService.deriveUserSeqNo(10L)).thenReturn("1000000010");
        when(ledgerMapper.findAccountByBankAndDigits("004", "12340104987654")).thenReturn(null);

        MockBankDto.QuickConnectStartRequest okReq = new MockBankDto.QuickConnectStartRequest();
        okReq.setOrganization("국민은행");
        okReq.setAccountNo("123401-04-987654");
        okReq.setHolderName("홍길동");
        okReq.setPhone("01012345678");
        MockBankDto.QuickConnectSessionResponse session = service.startQuickConnect(10L, okReq, "127.0.0.1");

        String wrongCode = "0000".equals(session.getAuthCodeHint()) ? "9999" : "0000";
        MockBankDto.QuickConnectVerifyRequest wrongReq = new MockBankDto.QuickConnectVerifyRequest();
        wrongReq.setAuthCode(wrongCode);
        wrongReq.setAgreed(true);

        for (int i = 0; i < 4; i++) {
            CodefException ex = assertThrows(
                    CodefException.class,
                    () -> service.verifyAndComplete(10L, session.getSessionToken(), wrongReq, "127.0.0.1")
            );
            assertEquals("QUICK_CONNECT_CODE_MISMATCH", ex.getCode());
        }
        CodefException maxEx = assertThrows(
                CodefException.class,
                () -> service.verifyAndComplete(10L, session.getSessionToken(), wrongReq, "127.0.0.1")
        );
        assertEquals("QUICK_CONNECT_MAX_ATTEMPTS", maxEx.getCode());
    }
}
