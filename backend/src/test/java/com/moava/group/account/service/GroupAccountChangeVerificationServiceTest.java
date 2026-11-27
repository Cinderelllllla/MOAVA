package com.moava.group.account.service;

import com.moava.group.account.dto.GroupAccountChangeVerifyResponse;
import com.moava.group.account.dto.GroupAccountVerificationTarget;
import com.moava.group.account.mapper.GroupAccountChangeMapper;
import com.moava.group.create.service.GroupAccountCrypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GroupAccountChangeVerificationServiceTest {

    @Mock
    private GroupAccountChangeMapper mapper;

    @Mock
    private GroupAccountCrypto crypto;

    @Mock
    private ObjectProvider<AccountVerificationProvider> providers;

    @Mock
    private AccountVerificationProvider provider;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private GroupAccountChangeVerificationService service;

    private final Long groupId = 1L;
    private final Long accountId = 100L;
    private final Long memberId = 10L;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // TransactionTemplate 실행에 필요한 Mock
        lenient().when(transactionManager.getTransaction(
                any(TransactionDefinition.class)
        )).thenReturn(transactionStatus);

        service = new GroupAccountChangeVerificationService(
                mapper,
                crypto,
                providers,
                transactionManager
        );
    }

    private GroupAccountVerificationTarget target(
            String verifyStatus
    ) {
        return new GroupAccountVerificationTarget(
                accountId,
                groupId,
                memberId,
                "테스트은행",
                "encrypted-account",
                "9012",
                1,
                verifyStatus,
                "PENDING"
        );
    }

    private void mockOwner() {
        when(mapper.findActiveOwnerName(groupId, memberId))
                .thenReturn("모임장");
    }

    private void mockAccount() {
        when(mapper.findPendingVerificationTarget(
            groupId, accountId
    )).thenReturn(
            target("PENDING"),
            (GroupAccountVerificationTarget) null);
    }

    private void mockCryptoAndProvider() {
        when(providers.getIfAvailable()).thenReturn(provider);
        when(crypto.keyVersion()).thenReturn(1);
        when(crypto.decrypt("encrypted-account"))
                .thenReturn("123456789012");
    }

    // 1. 인증 서비스 미연결
    @Test
    void CODEF_미연결이면_503() {
        mockOwner();
        mockAccount();

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.verify(groupId, accountId, memberId)
        );

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                ex.getStatusCode().value()
        );

        verify(mapper, never()).updatePendingAccountVerified(
                anyLong(), anyLong(), anyString(),
                anyString(), anyInt()
        );
    }

    // 2. 계좌 소유 확인 실패
    @Test
    void 소유인증_실패하면_422() {
        mockOwner();
        mockAccount();
        mockCryptoAndProvider();

        when(provider.verifyAccount(
                memberId, "테스트은행", "123456789012"
        )).thenReturn(false);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.verify(groupId, accountId, memberId)
        );

        assertEquals(422, ex.getStatusCode().value());
    }

    // 3. 외부 인증 API 요청 오류
    @Test
    void CODEF_요청오류면_502() {
        mockOwner();
        mockAccount();
        mockCryptoAndProvider();

        when(provider.verifyAccount(
                memberId, "테스트은행", "123456789012"
        )).thenThrow(new RuntimeException("External API error"));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.verify(groupId, accountId, memberId)
        );

        assertEquals(502, ex.getStatusCode().value());
    }

    // 4. 모임장이 아닌 회원 접근
    @Test
    void 모임장이_아니면_인증불가() {
        when(mapper.findActiveOwnerName(groupId, memberId))
                .thenReturn(null);

        assertThrows(
                SecurityException.class,
                () -> service.verify(groupId, accountId, memberId)
        );

        verify(mapper, never()).findPendingVerificationTarget(
                anyLong(), anyLong()
        );
    }

    // 5. 외부 인증 중 신청 취소
    @Test
    void 인증도중_신청취소되면_저장차단() {
        mockOwner();
        mockCryptoAndProvider();

        when(mapper.findPendingVerificationTarget(
                groupId, accountId
        )).thenReturn(
        target("PENDING"),
        (GroupAccountVerificationTarget) null
        );

        when(provider.verifyAccount(
                memberId, "테스트은행", "123456789012"
        )).thenReturn(true);

        when(mapper.lockGroup(groupId)).thenReturn(groupId);

        assertThrows(
                IllegalStateException.class,
                () -> service.verify(groupId, accountId, memberId)
        );

        verify(mapper, never()).updatePendingAccountVerified(
                anyLong(), anyLong(), anyString(),
                anyString(), anyInt()
        );
    }

    // 6. 정상 인증 성공
    @Test
    void 정상인증시_VERIFIED_상태저장() {
        mockOwner();
        mockCryptoAndProvider();

        when(mapper.findPendingVerificationTarget(
                groupId, accountId
        )).thenReturn(
                target("PENDING"),
                target("PENDING"),
                target("VERIFIED")
        );

        when(provider.verifyAccount(
                memberId, "테스트은행", "123456789012"
        )).thenReturn(true);

        when(mapper.lockGroup(groupId)).thenReturn(groupId);

        when(mapper.updatePendingAccountVerified(
                groupId, accountId,
                "encrypted-account", "테스트은행", 1
        )).thenReturn(1);

        when(mapper.insertChangeAudit(
                eq(groupId), eq(memberId), anyString()
        )).thenReturn(1);

        GroupAccountChangeVerifyResponse result =
                service.verify(groupId, accountId, memberId);

        assertEquals("VERIFIED", result.verifyStatus());
        assertEquals("PENDING", result.accountStatus());
        assertEquals("*****9012", result.accountMasked());

        verify(mapper).updatePendingAccountVerified(
                groupId, accountId,
                "encrypted-account", "테스트은행", 1
        );

        verify(mapper).insertChangeAudit(
                eq(groupId), eq(memberId), anyString()
        );
    }

    // 7. 감사 로그 저장 실패
    @Test
    void 감사로그_저장실패시_예외() {
        mockOwner();
        mockCryptoAndProvider();
        mockAccount();

        when(provider.verifyAccount(
                memberId, "테스트은행", "123456789012"
        )).thenReturn(true);

        when(mapper.lockGroup(groupId)).thenReturn(groupId);

        when(mapper.updatePendingAccountVerified(
                groupId, accountId,
                "encrypted-account", "테스트은행", 1
        )).thenReturn(1);

        when(mapper.insertChangeAudit(
                eq(groupId), eq(memberId), anyString()
        )).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> service.verify(groupId, accountId, memberId)
        );
    }

    // 8. 이미 인증 완료된 신청
    @Test
    void 이미_인증된_계좌는_중복인증하지_않음() {
        mockOwner();

        when(mapper.findPendingVerificationTarget(
                groupId, accountId
        )).thenReturn(target("VERIFIED"));

        when(mapper.lockGroup(groupId)).thenReturn(groupId);

        GroupAccountChangeVerifyResponse result =
                service.verify(groupId, accountId, memberId);

        assertEquals("VERIFIED", result.verifyStatus());
        assertEquals("PENDING", result.accountStatus());

        verify(providers, never()).getIfAvailable();

        verify(mapper, never()).insertChangeAudit(
                anyLong(), anyLong(), anyString()
        );
    }
}
