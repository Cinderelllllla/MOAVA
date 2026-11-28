package com.moava.openbanking;

import com.moava.crypto.service.CryptoKeyService;
import com.moava.openbanking.codef.CodefAccountMapper;
import com.moava.openbanking.codef.CodefAccountVerificationProvider;
import com.moava.openbanking.codef.CodefBankService;
import com.moava.openbanking.codef.CodefDto;
import com.moava.openbanking.codef.CodefException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodefAccountVerificationProviderTest {

    @Mock
    private CodefAccountMapper codefAccountMapper;
    @Mock
    private CryptoKeyService cryptoKeyService;
    @Mock
    private CodefBankService codefBankService;

    private CodefAccountVerificationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new CodefAccountVerificationProvider(codefAccountMapper, cryptoKeyService, codefBankService);
    }

    @Test
    void 기존_CODEF_인증완료_동일계좌는_외부호출없이_즉시_true_반환() {
        CodefDto.ConnectionRecord conn = new CodefDto.ConnectionRecord();
        conn.setMemberId(10L);
        conn.setOrganization("0088");
        conn.setConnectedIdEnc("ENC_CONN");
        conn.setVerifiedYn("Y");
        conn.setVerifiedAccountEnc("ENC_ACC");
        conn.setKeyVer(1);

        when(codefAccountMapper.findConnection(10L, "0088")).thenReturn(conn);
        when(cryptoKeyService.decrypt("ENC_ACC", 1)).thenReturn("110123456789");

        boolean result = provider.verifyAccount(10L, "신한은행", "110-123-456789");

        assertTrue(result);
        verify(codefBankService, never()).verifyRealAccount(any(), any(), any());
    }

    @Test
    void 미인증_연결상태에서는_CODEF_실계좌인증_호출후_결과반환() {
        CodefDto.ConnectionRecord conn = new CodefDto.ConnectionRecord();
        conn.setMemberId(10L);
        conn.setOrganization("0004");
        conn.setConnectedIdEnc("ENC_CONN");
        conn.setVerifiedYn("N");
        conn.setKeyVer(1);

        when(codefAccountMapper.findConnection(10L, "0004")).thenReturn(conn);

        CodefDto.VerifyAccountResponse verifyRes = new CodefDto.VerifyAccountResponse();
        verifyRes.setVerified(true);
        when(codefBankService.verifyRealAccount(eq(10L), any(CodefDto.VerifyAccountRequest.class), eq("internal-f19")))
                .thenReturn(verifyRes);

        boolean result = provider.verifyAccount(10L, "국민은행", "123401-04-987654");

        assertTrue(result);
        ArgumentCaptor<CodefDto.VerifyAccountRequest> reqCaptor =
                ArgumentCaptor.forClass(CodefDto.VerifyAccountRequest.class);
        verify(codefBankService).verifyRealAccount(eq(10L), reqCaptor.capture(), eq("internal-f19"));
        assertEquals("0004", reqCaptor.getValue().getOrganization());
        assertEquals("12340104987654", reqCaptor.getValue().getAccount());
        assertEquals(Boolean.TRUE, reqCaptor.getValue().getSyncToMockBank());
    }

    @Test
    void 예금주불일치_미연결_미지원은행은_false_외부장애는_예외전파() {
        // 1) 미지원 은행 -> false
        assertFalse(provider.verifyAccount(10L, "없는은행", "110123456789"));

        // 2) 미연결 상태 -> false
        when(codefAccountMapper.findConnection(10L, "0088")).thenReturn(null);
        assertFalse(provider.verifyAccount(10L, "신한은행", "110123456789"));

        // 3) 예금주 불일치 -> false (GroupAccountService에서 422 변환)
        CodefDto.ConnectionRecord conn = new CodefDto.ConnectionRecord();
        conn.setConnectedIdEnc("ENC_CONN");
        conn.setVerifiedYn("N");
        when(codefAccountMapper.findConnection(10L, "0088")).thenReturn(conn);
        when(codefBankService.verifyRealAccount(eq(10L), any(), eq("internal-f19")))
                .thenThrow(CodefException.forbidden("CODEF_HOLDER_MISMATCH", "불일치"));
        assertFalse(provider.verifyAccount(10L, "신한은행", "110123456789"));

        // 4) CODEF 외부 통신 장애 -> 예외 전파 (GroupAccountService에서 502 변환)
        when(codefBankService.verifyRealAccount(eq(10L), any(), eq("internal-f19")))
                .thenThrow(CodefException.badGateway("CODEF_NETWORK_ERROR", "네트워크 장애"));
        CodefException ex = assertThrows(
                CodefException.class,
                () -> provider.verifyAccount(10L, "신한은행", "110123456789")
        );
        assertEquals("CODEF_NETWORK_ERROR", ex.getCode());
    }

    @Test
    void 가상은행_활성계좌는_F19_통과_및_QUICK_OAUTH_불일치는_외부호출없이_false() {
        com.moava.openbanking.mapper.MockBankLedgerMapper ledgerMapper =
                mock(com.moava.openbanking.mapper.MockBankLedgerMapper.class);
        com.moava.openbanking.service.MockBankProvisionService provisionService =
                mock(com.moava.openbanking.service.MockBankProvisionService.class);

        CodefAccountVerificationProvider fullProvider = new CodefAccountVerificationProvider(
                codefAccountMapper, cryptoKeyService, codefBankService, ledgerMapper, provisionService
        );

        when(provisionService.deriveUserSeqNo(10L)).thenReturn("1000000010");

        // 1) CODEF_CONNECTION에 없어도 가상은행 본인 소유 ACTIVE 계좌면 즉시 true + LEADER_YN='N'이면 자동 승격
        when(codefAccountMapper.findConnection(10L, "0090")).thenReturn(null);
        com.moava.openbanking.dto.KftcRecordDto.AccountRecord mockAcc = new com.moava.openbanking.dto.KftcRecordDto.AccountRecord();
        mockAcc.setFintechUseNum("199090000010000000000001");
        mockAcc.setUserSeqNo("1000000010");
        mockAcc.setStatus("ACTIVE");
        mockAcc.setLeaderYn("N");
        when(ledgerMapper.findAccountByBankAndDigits("090", "3333012345678")).thenReturn(mockAcc);
        when(ledgerMapper.updateAccountLeaderYn(eq("199090000010000000000001"), eq("1000000010"), eq("Y"), any()))
                .thenReturn(1);

        assertTrue(fullProvider.verifyAccount(10L, "카카오뱅크", "3333-01-2345678"));
        verify(codefBankService, never()).verifyRealAccount(any(), any(), any());
        verify(ledgerMapper).updateAccountLeaderYn(eq("199090000010000000000001"), eq("1000000010"), eq("Y"), any());

        // 2) QUICK_OAUTH 마커 연결이지만 다른 미등록 계좌 조회 시 외부 CODEF 호출 없이 false
        CodefDto.ConnectionRecord quickConn = new CodefDto.ConnectionRecord();
        quickConn.setConnectedIdEnc("ENC_QUICK");
        quickConn.setVerifiedYn("Y");
        quickConn.setVerifiedAccountEnc("ENC_OTHER");
        quickConn.setKeyVer(1);
        when(codefAccountMapper.findConnection(10L, "0088")).thenReturn(quickConn);
        when(cryptoKeyService.decrypt("ENC_OTHER", 1)).thenReturn("110111111111");
        when(cryptoKeyService.decrypt("ENC_QUICK", 1)).thenReturn("QUICK_OAUTH:0088");
        when(ledgerMapper.findAccountByBankAndDigits("088", "110999999999")).thenReturn(null);

        assertFalse(fullProvider.verifyAccount(10L, "신한은행", "110-999-999999"));
        verify(codefBankService, never()).verifyRealAccount(any(), any(), any());
    }
}
