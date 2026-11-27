package com.moava.openbanking;

import com.moava.crypto.service.CryptoKeyService;
import com.moava.group.create.service.GroupAccountCrypto;
import com.moava.openbanking.client.OpenBankingProperties;
import com.moava.openbanking.codef.CodefAccountMapper;
import com.moava.openbanking.codef.CodefBankCode;
import com.moava.openbanking.codef.CodefBankService;
import com.moava.openbanking.codef.CodefDto;
import com.moava.openbanking.codef.CodefException;
import com.moava.openbanking.dto.KftcRecordDto;
import com.moava.openbanking.dto.KftcTransferDto;
import com.moava.openbanking.dto.MockBankDto;
import com.moava.openbanking.exception.KftcOpenBankingException;
import com.moava.openbanking.exception.KftcResponseCode;
import com.moava.openbanking.mapper.KftcOpenBankingMapper;
import com.moava.openbanking.mapper.MockBankLedgerMapper;
import com.moava.openbanking.service.KftcOpenBankingService;
import com.moava.openbanking.service.KftcSecurityGuard;
import com.moava.openbanking.service.MockBankAccountService;
import com.moava.openbanking.service.MockBankProvisionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MockBankAccountServiceTest {

    private static final String MY_FIN_NUM = "199088000010000000000001";
    private static final String LEADER_FIN_NUM = "199004000099000000000099";
    private static final String OTHER_LEADER_FIN_NUM = "199020000088000000000088";
    private static final String HASH_LEADER_KB = "hash_leader_kb_12340104987654";
    private static final String HASH_OTHER_SAME_LAST4 = "hash_other_99990000007654";
    private static final String HASH_MY_SHINHAN = "hash_my_shinhan_110123456789";

    @Mock
    private MockBankLedgerMapper ledgerMapper;
    @Mock
    private KftcOpenBankingMapper kftcMapper;
    @Mock
    private CodefAccountMapper codefAccountMapper;
    @Mock
    private CryptoKeyService cryptoKeyService;
    @Mock
    private KftcOpenBankingService kftcOpenBankingService;
    @Mock
    private CodefBankService codefBankService;
    @Mock
    private MockBankProvisionService provisionService;
    @Mock
    private GroupAccountCrypto groupAccountCrypto;

    private MockBankAccountService service;

    @BeforeEach
    void setUp() {
        OpenBankingProperties props = new OpenBankingProperties(
                "http://localhost:8080",
                "M202600001",
                "moava-demo-client",
                "moava-demo-secret"
        );
        service = new MockBankAccountService(
                ledgerMapper,
                kftcMapper,
                codefAccountMapper,
                cryptoKeyService,
                kftcOpenBankingService,
                codefBankService,
                provisionService,
                new KftcSecurityGuard(),
                props,
                groupAccountCrypto
        );
    }

    @Test
    void getOverview_계좌합산_월별집계_및_내모임_현재계좌_HMAC매칭() {
        when(codefAccountMapper.findActiveMemberName(10L)).thenReturn("홍길동");
        when(provisionService.deriveUserSeqNo(10L)).thenReturn("1000000010");

        CodefDto.ConnectionSummary verifiedConn = new CodefDto.ConnectionSummary();
        verifiedConn.setOrganization("0088");
        verifiedConn.setVerified(true);
        CodefDto.ConnectionSummary unverifiedConn = new CodefDto.ConnectionSummary();
        unverifiedConn.setOrganization("0004");
        unverifiedConn.setVerified(false);
        when(codefBankService.getMyConnections(10L)).thenReturn(List.of(verifiedConn, unverifiedConn));

        KftcRecordDto.AccountRecord acc = account(MY_FIN_NUM, "088", "신한은행", "110123456789", "홍길동", "1000000010", 700_000L, "N");
        when(ledgerMapper.findAccountsByUserSeqNo("1000000010")).thenReturn(List.of(acc));

        KftcRecordDto.TransactionRecord txIn = new KftcRecordDto.TransactionRecord();
        txIn.setTxId(1L);
        txIn.setBankTranId("M202600001U000000001");
        txIn.setFintechUseNum(MY_FIN_NUM);
        txIn.setInoutType("입금");
        txIn.setTranAmt(200_000L);
        txIn.setAfterBalanceAmt(700_000L);
        txIn.setPrintContent("급여");
        txIn.setTranAt(LocalDateTime.of(2026, 11, 20, 9, 0, 0));

        KftcRecordDto.TransactionRecord txOut = new KftcRecordDto.TransactionRecord();
        txOut.setTxId(2L);
        txOut.setBankTranId("M202600001U000000002");
        txOut.setFintechUseNum(MY_FIN_NUM);
        txOut.setInoutType("출금");
        txOut.setTranAmt(50_000L);
        txOut.setAfterBalanceAmt(650_000L);
        txOut.setPrintContent("A&B카페");
        txOut.setTranAt(LocalDateTime.of(2026, 11, 22, 14, 0, 0));

        when(ledgerMapper.findRecentTransactionsByUserSeqNo("1000000010", 30)).thenReturn(List.of(txOut, txIn));

        MockBankDto.MemberGroupInfo myGroup = new MockBankDto.MemberGroupInfo();
        myGroup.setGroupId(5L);
        myGroup.setTitle("제주도 여행계");
        myGroup.setOwnerId(99L);
        myGroup.setAmount(50_000L);
        myGroup.setAccountBankName("국민은행");
        myGroup.setAccountLast4("7654");
        myGroup.setAccountNoHash(HASH_LEADER_KB);
        myGroup.setAccountVerifyStatus("VERIFIED");
        when(ledgerMapper.findMemberActiveGroups(10L)).thenReturn(List.of(myGroup));

        // 뒤 4자리가 같은 타 계좌(해시 불일치)는 제외되고 현재 모임 계좌만 매칭
        KftcRecordDto.AccountRecord sameLast4Other = account(OTHER_LEADER_FIN_NUM, "004", "국민은행", "99990000007654", "박총무", "1000000088", 100_000L, "Y");
        KftcRecordDto.AccountRecord myGroupLeader = account(LEADER_FIN_NUM, "004", "국민은행", "12340104987654", "김모임", "1000000099", 500_000L, "Y");
        when(ledgerMapper.findActiveLeaderAccountsByBankAndLast4("004", "7654"))
                .thenReturn(List.of(sameLast4Other, myGroupLeader));
        when(groupAccountCrypto.hashAccountNumber("99990000007654")).thenReturn(HASH_OTHER_SAME_LAST4);
        when(groupAccountCrypto.hashAccountNumber("12340104987654")).thenReturn(HASH_LEADER_KB);

        MockBankDto.OverviewResponse res = service.getOverview(10L);

        assertEquals(1, res.getCodefVerifiedCount());
        assertEquals(1, res.getMockAccountCount());
        assertEquals(700_000L, res.getTotalBalanceAmt());
        assertEquals(2, res.getRecentTransactions().size());
        assertEquals("A&B카페", res.getRecentTransactions().get(0).getPrintContent());

        assertEquals(1, res.getMonthlySummary().size());
        assertEquals("202611", res.getMonthlySummary().get(0).getYearMonth());
        assertEquals(200_000L, res.getMonthlySummary().get(0).getTotalDepositAmt());
        assertEquals(50_000L, res.getMonthlySummary().get(0).getTotalWithdrawAmt());
        assertEquals(150_000L, res.getMonthlySummary().get(0).getNetAmt());

        assertEquals(1, res.getLeaderTargets().size());
        assertTrue(res.getLeaderTargets().get(0).isMyGroup());
        assertEquals(LEADER_FIN_NUM, res.getLeaderTargets().get(0).getFintechUseNum());
        assertEquals(5L, res.getLeaderTargets().get(0).getGroupId());
        assertEquals("VERIFIED", res.getLeaderTargets().get(0).getAccountVerifyStatus());
        assertTrue(res.getLeaderTargets().get(0).isAccountVerified());
    }

    @Test
    void getOverview_SQL월별집계사용_및_모임장목록_본인제외_다중모임중복제거() {
        when(codefAccountMapper.findActiveMemberName(10L)).thenReturn("홍길동");
        when(provisionService.deriveUserSeqNo(10L)).thenReturn("1000000010");
        when(codefBankService.getMyConnections(10L)).thenReturn(List.of());

        KftcRecordDto.AccountRecord myLeaderAcc = account(MY_FIN_NUM, "088", "신한은행", "110123456789", "홍길동", "1000000010", 700_000L, "Y");
        when(ledgerMapper.findAccountsByUserSeqNo("1000000010")).thenReturn(List.of(myLeaderAcc));
        when(ledgerMapper.findRecentTransactionsByUserSeqNo("1000000010", 30)).thenReturn(List.of());

        MockBankDto.MonthlyAggregate sqlMonth = new MockBankDto.MonthlyAggregate();
        sqlMonth.setYearMonth("202610");
        sqlMonth.setTotalDepositAmt(500_000L);
        sqlMonth.setTotalWithdrawAmt(120_000L);
        sqlMonth.setTxCount(40);
        when(ledgerMapper.findMonthlySummaryByUserSeqNo("1000000010")).thenReturn(List.of(sqlMonth));

        MockBankDto.MemberGroupInfo g1 = new MockBankDto.MemberGroupInfo();
        g1.setGroupId(5L);
        g1.setTitle("제주도 여행계");
        g1.setOwnerId(99L);
        g1.setAmount(50_000L);
        g1.setAccountBankName("국민은행");
        g1.setAccountLast4("7654");
        g1.setAccountNoHash(HASH_LEADER_KB);
        g1.setAccountVerifyStatus("REVERIFY_REQUIRED");
        MockBankDto.MemberGroupInfo g2 = new MockBankDto.MemberGroupInfo();
        g2.setGroupId(6L);
        g2.setTitle("부산 먹방계");
        g2.setOwnerId(99L);
        g2.setAmount(100_000L);
        g2.setAccountBankName("국민은행");
        g2.setAccountLast4("7654");
        g2.setAccountNoHash(HASH_LEADER_KB);
        g2.setAccountVerifyStatus("VERIFIED");
        MockBankDto.MemberGroupInfo myOwnGroup = new MockBankDto.MemberGroupInfo();
        myOwnGroup.setGroupId(7L);
        myOwnGroup.setTitle("내가 만든 모임");
        myOwnGroup.setOwnerId(10L);
        myOwnGroup.setAmount(200_000L);
        myOwnGroup.setAccountBankName("신한은행");
        myOwnGroup.setAccountLast4("6789");
        myOwnGroup.setAccountNoHash(HASH_MY_SHINHAN);
        myOwnGroup.setAccountVerifyStatus("VERIFIED");
        when(ledgerMapper.findMemberActiveGroups(10L)).thenReturn(List.of(g1, g2, myOwnGroup));

        KftcRecordDto.AccountRecord otherLeaderAcc = account(LEADER_FIN_NUM, "004", "국민은행", "12340104987654", "김모임", "1000000099", 500_000L, "Y");
        when(ledgerMapper.findActiveLeaderAccountsByBankAndLast4("004", "7654")).thenReturn(List.of(otherLeaderAcc));
        when(ledgerMapper.findActiveLeaderAccountsByBankAndLast4("088", "6789")).thenReturn(List.of(myLeaderAcc));
        when(groupAccountCrypto.hashAccountNumber("12340104987654")).thenReturn(HASH_LEADER_KB);
        when(groupAccountCrypto.hashAccountNumber("110123456789")).thenReturn(HASH_MY_SHINHAN);

        MockBankDto.OverviewResponse res = service.getOverview(10L);

        assertEquals(1, res.getMonthlySummary().size());
        assertEquals("202610", res.getMonthlySummary().get(0).getYearMonth());
        assertEquals(380_000L, res.getMonthlySummary().get(0).getNetAmt());
        assertEquals(40, res.getMonthlySummary().get(0).getTxCount());

        // 본인 모임장 계좌는 제외되고, 2개 모임을 가진 타 모임장 계좌는 1건으로 병합
        assertEquals(1, res.getLeaderTargets().size());
        assertEquals(LEADER_FIN_NUM, res.getLeaderTargets().get(0).getFintechUseNum());
        assertEquals("제주도 여행계 외 1개 모임", res.getLeaderTargets().get(0).getGroupTitle());
        assertTrue(res.getLeaderTargets().get(0).isAccountVerified());
    }

    @Test
    void syncFromCodef_저장인증정보_재사용_실시간재조회_및_미인증_차단() {
        when(codefAccountMapper.findActiveMemberName(10L)).thenReturn("홍길동");

        // 1) refreshFromCodef=false, 저장된 인증 계좌로 적재 (CODEF 외부 호출 없음)
        CodefDto.ConnectionRecord verified = new CodefDto.ConnectionRecord();
        verified.setVerifiedYn("Y");
        verified.setVerifiedAccountEnc("ENC_ACC");
        verified.setVerifiedHolderName("홍길동");
        verified.setKeyVer(1);
        when(codefAccountMapper.findConnection(10L, "0088")).thenReturn(verified);
        when(cryptoKeyService.decrypt("ENC_ACC", 1)).thenReturn("110123456789");

        MockBankDto.ProvisionResult localResult = new MockBankDto.ProvisionResult();
        localResult.setFintechUseNum(MY_FIN_NUM);
        when(provisionService.provisionFromCodef(
                eq(10L), eq(CodefBankCode.SHINHAN), eq("110123456789"), eq("홍길동"),
                isNull(), anyList(), eq(500_000L), isNull(), eq(false)
        )).thenReturn(localResult);

        MockBankDto.SyncFromCodefRequest localReq = new MockBankDto.SyncFromCodefRequest();
        localReq.setOrganization("0088");
        localReq.setRefreshFromCodef(false);
        localReq.setInitialMockBalance(500_000L);

        MockBankDto.ProvisionResult res1 = service.syncFromCodef(10L, localReq, "127.0.0.1");
        assertEquals(MY_FIN_NUM, res1.getFintechUseNum());
        verify(codefBankService, never()).refreshVerifiedAccountToMockBank(any(), any(), any(), any(), any(), any(), any(), any(), any());

        // 2) refreshFromCodef=true -> CodefBankService.refreshVerifiedAccountToMockBank 위임
        MockBankDto.SyncFromCodefRequest refreshReq = new MockBankDto.SyncFromCodefRequest();
        refreshReq.setOrganization("국민은행");
        refreshReq.setRefreshFromCodef(true);
        MockBankDto.ProvisionResult remoteResult = new MockBankDto.ProvisionResult();
        remoteResult.setFintechUseNum(LEADER_FIN_NUM);
        when(codefBankService.refreshVerifiedAccountToMockBank(
                eq(10L), eq(CodefBankCode.KB), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq("127.0.0.1")
        )).thenReturn(remoteResult);

        MockBankDto.ProvisionResult res2 = service.syncFromCodef(10L, refreshReq, "127.0.0.1");
        assertEquals(LEADER_FIN_NUM, res2.getFintechUseNum());

        // 3) 미인증 상태 -> CODEF_NOT_VERIFIED
        CodefDto.ConnectionRecord unverified = new CodefDto.ConnectionRecord();
        unverified.setVerifiedYn("N");
        when(codefAccountMapper.findConnection(10L, "0004")).thenReturn(unverified);

        MockBankDto.SyncFromCodefRequest failReq = new MockBankDto.SyncFromCodefRequest();
        failReq.setOrganization("0004");
        failReq.setRefreshFromCodef(false);
        CodefException ex = assertThrows(
                CodefException.class,
                () -> service.syncFromCodef(10L, failReq, "127.0.0.1")
        );
        assertEquals("CODEF_NOT_VERIFIED", ex.getCode());
    }

    @Test
    void updateLeaderFlag_소유계좌_변경성공_및_미소유_404() {
        when(codefAccountMapper.findActiveMemberName(10L)).thenReturn("홍길동");
        when(provisionService.deriveUserSeqNo(10L)).thenReturn("1000000010");
        when(ledgerMapper.updateAccountLeaderYn(eq(MY_FIN_NUM), eq("1000000010"), eq("Y"), any())).thenReturn(1);
        when(kftcMapper.findAccountByFintechUseNum(MY_FIN_NUM))
                .thenReturn(account(MY_FIN_NUM, "088", "신한은행", "110123456789", "홍길동", "1000000010", 100_000L, "Y"));

        MockBankDto.MockAccountView updated = service.updateLeaderFlag(10L, MY_FIN_NUM, true);
        assertTrue(updated.isLeaderAccount());
        assertEquals("Y", updated.getLeaderYn());

        when(ledgerMapper.updateAccountLeaderYn(eq(LEADER_FIN_NUM), eq("1000000010"), eq("Y"), any())).thenReturn(0);
        CodefException notFound = assertThrows(
                CodefException.class,
                () -> service.updateLeaderFlag(10L, LEADER_FIN_NUM, true)
        );
        assertEquals("MOCK_BANK_ACCOUNT_NOT_FOUND", notFound.getCode());
        assertEquals(404, notFound.getHttpStatus().value());
    }

    @Test
    void depositDemoFunds_본인계좌충전_성공_및_타인계좌_차단() {
        when(codefAccountMapper.findActiveMemberName(10L)).thenReturn("홍길동");
        when(provisionService.deriveUserSeqNo(10L)).thenReturn("1000000010");

        KftcRecordDto.AccountRecord myAcc = account(MY_FIN_NUM, "088", "신한은행", "110123456789", "홍길동", "1000000010", 100_000L, "N");
        when(kftcMapper.findAccountForUpdate(MY_FIN_NUM)).thenReturn(myAcc);
        when(kftcMapper.creditAccountBalance(eq(MY_FIN_NUM), eq(50_000L), any())).thenReturn(1);

        MockBankDto.DemoDepositRequest req = new MockBankDto.DemoDepositRequest();
        req.setAmount(50_000L);
        req.setPrintContent("시연 충전");

        MockBankDto.DemoDepositResponse res = service.depositDemoFunds(10L, MY_FIN_NUM, req, "127.0.0.1");
        assertEquals(50_000L, res.getDepositAmt());
        assertEquals(150_000L, res.getAfterBalanceAmt());
        assertEquals("시연 충전", res.getPrintContent());
        verify(kftcMapper).insertTransaction(any(KftcRecordDto.TransactionRecord.class));

        // 타인 계좌 충전 시도 -> INSUFFICIENT_SCOPE
        KftcRecordDto.AccountRecord otherAcc = account(LEADER_FIN_NUM, "004", "국민은행", "12340104987654", "김모임", "1000000099", 500_000L, "Y");
        when(kftcMapper.findAccountForUpdate(LEADER_FIN_NUM)).thenReturn(otherAcc);

        KftcOpenBankingException forbidden = assertThrows(
                KftcOpenBankingException.class,
                () -> service.depositDemoFunds(10L, LEADER_FIN_NUM, req, "127.0.0.1")
        );
        assertEquals(KftcResponseCode.INSUFFICIENT_SCOPE, forbidden.getResponseCode());
    }

    @Test
    void transferToLeader_KFTC엔진_위임_및_수취인_마스킹() {
        when(codefAccountMapper.findActiveMemberName(10L)).thenReturn("홍길동");
        when(provisionService.deriveUserSeqNo(10L)).thenReturn("1000000010");

        MockBankDto.MemberGroupInfo myGroup = new MockBankDto.MemberGroupInfo();
        myGroup.setGroupId(5L);
        myGroup.setTitle("제주도 여행계");
        myGroup.setOwnerId(99L);
        myGroup.setAmount(50_000L);
        myGroup.setAccountBankName("국민은행");
        myGroup.setAccountLast4("7654");
        myGroup.setAccountNoHash(HASH_LEADER_KB);
        myGroup.setAccountVerifyStatus("VERIFIED");
        when(ledgerMapper.findMemberActiveGroups(10L)).thenReturn(List.of(myGroup));

        KftcRecordDto.AccountRecord myGroupLeader = account(LEADER_FIN_NUM, "004", "국민은행", "12340104987654", "김모임", "1000000099", 500_000L, "Y");
        when(ledgerMapper.findActiveLeaderAccountsByBankAndLast4("004", "7654")).thenReturn(List.of(myGroupLeader));
        when(groupAccountCrypto.hashAccountNumber("12340104987654")).thenReturn(HASH_LEADER_KB);

        KftcTransferDto.TransferResponse kftcRes = new KftcTransferDto.TransferResponse();
        kftcRes.setBankTranId("M202600001U123456789");
        kftcRes.setBankTranDate("20261126");
        kftcRes.setWdFintechUseNum(MY_FIN_NUM);
        kftcRes.setWdBankName("신한은행");
        kftcRes.setWdAccountHolderName("홍길동");
        kftcRes.setWdAfterBalanceAmt(450_000L);
        kftcRes.setDpsFintechUseNum(LEADER_FIN_NUM);
        kftcRes.setDpsBankName("국민은행");
        kftcRes.setDpsAccountHolderName("김모임");
        kftcRes.setTranAmt(50_000L);
        kftcRes.setPrintContent("11월 회비");

        when(kftcOpenBankingService.withdrawTransfer(any(), any(), eq("127.0.0.1"))).thenReturn(kftcRes);

        MockBankDto.MemberTransferRequest req = new MockBankDto.MemberTransferRequest();
        req.setFromFintechUseNum(MY_FIN_NUM);
        req.setToFintechUseNum(LEADER_FIN_NUM);
        req.setAmount(50_000L);
        req.setWdPrintContent("11월 회비");

        MockBankDto.MemberTransferResponse res = service.transferToLeader(10L, req, "127.0.0.1");

        assertEquals("M202600001U123456789", res.getBankTranId());
        assertEquals(450_000L, res.getFromAfterBalanceAmt());
        assertEquals(50_000L, res.getTranAmt());
        assertEquals("김○임", res.getToHolderNameMasked());

        ArgumentCaptor<KftcRecordDto.OAuthTokenRecord> tokCaptor = ArgumentCaptor.forClass(KftcRecordDto.OAuthTokenRecord.class);
        verify(kftcOpenBankingService).withdrawTransfer(any(), tokCaptor.capture(), eq("127.0.0.1"));
        assertEquals("1000000010", tokCaptor.getValue().getUserSeqNo());
        assertEquals("transfer", tokCaptor.getValue().getScope());
    }

    @Test
    void transferToLeader_미참여모임_계좌이체_차단() {
        when(codefAccountMapper.findActiveMemberName(10L)).thenReturn("홍길동");
        when(provisionService.deriveUserSeqNo(10L)).thenReturn("1000000010");
        when(ledgerMapper.findMemberActiveGroups(10L)).thenReturn(List.of());

        MockBankDto.MemberTransferRequest req = new MockBankDto.MemberTransferRequest();
        req.setFromFintechUseNum(MY_FIN_NUM);
        req.setToFintechUseNum(OTHER_LEADER_FIN_NUM);
        req.setAmount(50_000L);

        CodefException ex = assertThrows(
                CodefException.class,
                () -> service.transferToLeader(10L, req, "127.0.0.1")
        );
        assertEquals("MOCK_BANK_TARGET_NOT_ALLOWED", ex.getCode());
        assertEquals(403, ex.getHttpStatus().value());
        verify(kftcOpenBankingService, never()).withdrawTransfer(any(), any(), any());
    }

    private static KftcRecordDto.AccountRecord account(
            String finNum, String bankCode, String bankName, String accNo,
            String holder, String userSeqNo, long bal, String leaderYn
    ) {
        KftcRecordDto.AccountRecord r = new KftcRecordDto.AccountRecord();
        r.setFintechUseNum(finNum);
        r.setBankCode(bankCode);
        r.setBankName(bankName);
        r.setAccountNo(accNo);
        r.setHolderName(holder);
        r.setUserSeqNo(userSeqNo);
        r.setBalance(bal);
        r.setLeaderYn(leaderYn);
        r.setStatus("ACTIVE");
        return r;
    }
}
