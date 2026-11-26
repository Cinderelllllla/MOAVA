package com.moava.openbanking;

import com.moava.openbanking.client.OpenBankingProperties;
import com.moava.openbanking.codef.CodefBankCode;
import com.moava.openbanking.codef.CodefDto;
import com.moava.openbanking.codef.CodefException;
import com.moava.openbanking.dto.KftcRecordDto;
import com.moava.openbanking.dto.MockBankDto;
import com.moava.openbanking.mapper.KftcOpenBankingMapper;
import com.moava.openbanking.mapper.MockBankLedgerMapper;
import com.moava.openbanking.service.KftcSecurityGuard;
import com.moava.openbanking.service.MockBankProvisionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MockBankProvisionServiceTest {

    @Mock
    private MockBankLedgerMapper ledgerMapper;
    @Mock
    private KftcOpenBankingMapper kftcMapper;

    private MockBankProvisionService service;

    @BeforeEach
    void setUp() {
        OpenBankingProperties props = new OpenBankingProperties(
                "http://localhost:8080",
                "M202600001",
                "moava-demo-client",
                "moava-demo-secret"
        );
        service = new MockBankProvisionService(ledgerMapper, kftcMapper, new KftcSecurityGuard(), props);
    }

    @Test
    void deriveUserSeqNo_결정적_규격_생성_및_비정상_회원ID_차단() {
        assertEquals("1000000001", service.deriveUserSeqNo(1L));
        assertEquals("1000000042", service.deriveUserSeqNo(42L));
        assertEquals("11000000000", service.deriveUserSeqNo(1_000_000_000L));

        assertThrows(CodefException.class, () -> service.deriveUserSeqNo(null));
        assertThrows(CodefException.class, () -> service.deriveUserSeqNo(0L));
    }

    @Test
    void 신규계좌_적재시_24자리_핀테크번호_모임장자동판별_거래적재_및_중복스킵() {
        when(ledgerMapper.findAccountByBankAndDigits("088", "110123456789")).thenReturn(null);
        when(ledgerMapper.countOwnedActiveGroups(10L)).thenReturn(1);
        when(ledgerMapper.countByFintechUseNum(anyString())).thenReturn(0);
        when(kftcMapper.countByBankTranId(anyString())).thenReturn(0, 1);

        CodefDto.TransactionItem tx1 = codefTx("20261124", "101500", "입금", 50_000L, 550_000L, "모임회비", "홍길동");
        CodefDto.TransactionItem tx2 = codefTx("20261123", "090000", "출금", 12_000L, 500_000L, "A&B카페", "스타벅스");

        MockBankDto.ProvisionResult res = service.provisionFromCodef(
                10L,
                CodefBankCode.SHINHAN,
                "110123456789",
                "홍길동",
                550_000L,
                List.of(tx1, tx2),
                null,
                null,
                true
        );

        assertTrue(res.isCreated());
        assertEquals("1000000010", res.getUserSeqNo());
        assertEquals(24, res.getFintechUseNum().length());
        assertTrue(res.getFintechUseNum().matches("^[0-9]{24}$"));
        assertEquals("Y", res.getLeaderYn());
        assertTrue(res.isLeaderAccount());
        assertEquals(550_000L, res.getBalanceAmt());
        assertEquals("****6789", res.getAccountMasked());
        assertEquals(1, res.getSyncedTransactionCount());
        assertEquals(1, res.getSkippedDuplicateCount());
        assertFalse(res.isOpeningSnapshotCreated());

        ArgumentCaptor<KftcRecordDto.AccountRecord> accCaptor = ArgumentCaptor.forClass(KftcRecordDto.AccountRecord.class);
        verify(ledgerMapper).insertMockAccount(accCaptor.capture());
        assertEquals("ACTIVE", accCaptor.getValue().getStatus());
        assertEquals("Y", accCaptor.getValue().getLeaderYn());

        verify(kftcMapper, times(1)).insertTransaction(any(KftcRecordDto.TransactionRecord.class));
    }

    @Test
    void 기존계좌_갱신_잔액옵션_및_타회원_계좌충돌_403_차단() {
        // 1) 타 회원 소유 계좌 -> 403 MOCK_BANK_ACCOUNT_CONFLICT
        KftcRecordDto.AccountRecord otherAcc = new KftcRecordDto.AccountRecord();
        otherAcc.setFintechUseNum("199088000099000000000001");
        otherAcc.setUserSeqNo("1000000099");
        otherAcc.setBalance(100_000L);
        otherAcc.setLeaderYn("N");

        when(ledgerMapper.findAccountByBankAndDigits("088", "110123456789")).thenReturn(otherAcc);
        CodefException conflict = assertThrows(
                CodefException.class,
                () -> service.provisionFromCodef(
                        10L, CodefBankCode.SHINHAN, "110123456789", "홍길동",
                        500_000L, Collections.emptyList(), null, null, true
                )
        );
        assertEquals("MOCK_BANK_ACCOUNT_CONFLICT", conflict.getCode());
        assertEquals(403, conflict.getHttpStatus().value());

        // 2) 본인 기존 계좌, updateBalanceIfExists=false -> 기존 잔액 유지
        KftcRecordDto.AccountRecord myAcc = new KftcRecordDto.AccountRecord();
        myAcc.setFintechUseNum("199088000010000000000001");
        myAcc.setUserSeqNo("1000000010");
        myAcc.setBalance(300_000L);
        myAcc.setLeaderYn("N");

        when(ledgerMapper.findAccountByBankAndDigits("088", "110123456789")).thenReturn(myAcc);
        when(ledgerMapper.countOwnedActiveGroups(10L)).thenReturn(0);
        when(ledgerMapper.countTransactionsByFintechUseNum("199088000010000000000001")).thenReturn(3);

        MockBankDto.ProvisionResult keepBal = service.provisionFromCodef(
                10L, CodefBankCode.SHINHAN, "110123456789", "홍길동",
                999_000L, Collections.emptyList(), null, null, false
        );
        assertFalse(keepBal.isCreated());
        assertEquals(300_000L, keepBal.getBalanceAmt());
        verify(ledgerMapper).updateMockAccountFromSync(
                eq("199088000010000000000001"), eq("홍길동"), eq("1000000010"), eq(300_000L), eq("N"), any()
        );

        // 3) updateBalanceIfExists=true -> 실잔액으로 갱신
        MockBankDto.ProvisionResult updatedBal = service.provisionFromCodef(
                10L, CodefBankCode.SHINHAN, "110123456789", "홍길동",
                999_000L, Collections.emptyList(), null, Boolean.TRUE, true
        );
        assertEquals(999_000L, updatedBal.getBalanceAmt());
        assertTrue(updatedBal.isLeaderAccount());
    }

    @Test
    void 실거래내역이_비어있고_잔액이_양수면_초기잔액_스냅샷_1건_생성() {
        when(ledgerMapper.findAccountByBankAndDigits("004", "12340104987654")).thenReturn(null);
        when(ledgerMapper.countOwnedActiveGroups(20L)).thenReturn(0);
        when(ledgerMapper.countByFintechUseNum(anyString())).thenReturn(0);
        when(ledgerMapper.countTransactionsByFintechUseNum(anyString())).thenReturn(0);
        when(kftcMapper.countByBankTranId(anyString())).thenReturn(0);

        MockBankDto.ProvisionResult res = service.provisionFromCodef(
                20L,
                CodefBankCode.KB,
                "12340104987654",
                "김모아",
                250_000L,
                Collections.emptyList(),
                null,
                null,
                true
        );

        assertTrue(res.isOpeningSnapshotCreated());
        assertEquals(1, res.getSyncedTransactionCount());

        ArgumentCaptor<KftcRecordDto.TransactionRecord> txCaptor = ArgumentCaptor.forClass(KftcRecordDto.TransactionRecord.class);
        verify(kftcMapper).insertTransaction(txCaptor.capture());
        assertEquals("입금", txCaptor.getValue().getInoutType());
        assertEquals(250_000L, txCaptor.getValue().getTranAmt());
        assertEquals("실계좌 연동 초기잔액", txCaptor.getValue().getPrintContent());
    }

    @Test
    void sanitizePlainText_UTF8바이트절단_및_스크립트제거_이중이스케이프없음() {
        // 한글 3바이트 문자 안전 절단 (10바이트 한도 -> 최대 3글자 9바이트)
        String truncated = MockBankProvisionService.sanitizePlainText("가나다라마바사", 10, "기본값");
        assertTrue(truncated.getBytes(StandardCharsets.UTF_8).length <= 10);
        assertEquals("가나다", truncated);

        // 특수문자(&)는 HTML 엔티티로 이중 이스케이프되지 않고 태그 기호와 제어문자만 정제
        String cleaned = MockBankProvisionService.sanitizePlainText("A&B <script>카페\u0000", 50, "기본값");
        assertTrue(cleaned.contains("A&B"));
        assertFalse(cleaned.contains("&amp;"));
        assertFalse(cleaned.contains("<"));
        assertFalse(cleaned.contains(">"));

        // 위험 URI 스킴 무력화
        String blocked = MockBankProvisionService.sanitizePlainText("javascript:alert(1)", 50, "기본값");
        assertTrue(blocked.startsWith("blocked:"));
    }

    private static CodefDto.TransactionItem codefTx(
            String date, String time, String inout, long amt, long afterBal, String desc1, String desc3
    ) {
        CodefDto.TransactionItem item = new CodefDto.TransactionItem();
        item.setTranDate(date);
        item.setTranTime(time);
        item.setInoutType(inout);
        item.setTranAmt(amt);
        item.setAfterBalanceAmt(afterBal);
        item.setDesc1(desc1);
        item.setDesc3(desc3);
        return item;
    }
}
