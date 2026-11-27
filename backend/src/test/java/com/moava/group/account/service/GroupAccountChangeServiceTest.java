package com.moava.group.account.service;

import com.moava.group.account.dto.GroupAccountChangeRequest;
import com.moava.group.account.dto.GroupAccountChangeResponse;
import com.moava.group.account.dto.GroupAccountConsentRequest;
import com.moava.group.account.dto.GroupAccountConsentResponse;
import com.moava.group.account.mapper.GroupAccountChangeMapper;
import com.moava.group.create.service.GroupAccountCrypto;
import com.moava.group.account.dto.GroupAccountConsentStatusResponse;
import org.springframework.web.server.ResponseStatusException;
import com.moava.group.account.dto.GroupAccountChangeCloseResponse;
import com.moava.group.account.dto.GroupAccountChangeReadinessResponse;
import com.moava.group.account.dto.GroupAccountVerificationTarget;
import com.moava.group.account.dto.GroupAccountConsentTargetInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GroupAccountChangeServiceTest {

    @Mock
    private GroupAccountChangeMapper mapper;

    @Mock
    private GroupAccountCrypto crypto;

    private GroupAccountChangeService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        service = new GroupAccountChangeService(
                mapper,
                crypto
        );
    }

    @Test
    void 정상_계좌변경_신청() {
        Long groupId = 1L;
        Long memberId = 10L;

        GroupAccountChangeRequest request =
                new GroupAccountChangeRequest(
                        "국민은행",
                        "123456789012"
                );

        when(mapper.lockGroup(groupId)).thenReturn(groupId);
        when(mapper.findActiveOwnerName(groupId, memberId))
                .thenReturn("테스트회원");
        when(mapper.countCurrentAccount(groupId)).thenReturn(1);
        when(mapper.countPendingChange(groupId)).thenReturn(0);
        when(mapper.countDuplicateAccount(anyString())).thenReturn(0);
        when(mapper.nextAccountId()).thenReturn(100L);
        when(mapper.insertPendingAccount(
                anyLong(), anyLong(), anyString(),
                anyString(), anyString(), anyString(),
                anyString(), anyInt(), anyLong()
        )).thenReturn(1);
        when(mapper.insertChangeAudit(
                anyLong(), anyLong(), anyString()
        )).thenReturn(1);

        when(crypto.hashAccountNumber(anyString()))
                .thenReturn("test-hash");
        when(crypto.encrypt(anyString()))
                .thenReturn("encrypted");
        when(crypto.keyVersion()).thenReturn(1);

        GroupAccountChangeResponse response =
                service.requestAccountChange(
                        groupId, memberId, request
                );

        assertEquals(100L, response.gyeAccountId());
        assertEquals("PENDING", response.accountStatus());
        assertEquals("PENDING", response.verifyStatus());
        assertEquals("*****9012", response.accountMasked());

        verify(mapper).insertPendingAccount(
                eq(100L),
                eq(groupId),
                eq("국민은행"),
                anyString(),
                anyString(),
                eq("9012"),
                anyString(),
                eq(1),
                eq(memberId)
        );
    }

    @Test
    void 모임장이_아니면_변경신청_불가() {
        Long groupId = 1L;
        Long memberId = 20L;

        when(mapper.lockGroup(groupId)).thenReturn(groupId);
        when(mapper.findActiveOwnerName(groupId, memberId))
                .thenReturn(null);

        assertThrows(SecurityException.class, () ->
                service.requestAccountChange(
                        groupId,
                        memberId,
                        new GroupAccountChangeRequest(
                                "국민은행",
                                "123456789012"
                        )
                )
        );

        verify(mapper, never()).insertPendingAccount(
                anyLong(), anyLong(), anyString(),
                anyString(), anyString(), anyString(),
                anyString(), anyInt(), anyLong()
        );
    }

    @Test
    void 진행중인_변경신청이_있으면_중복신청_불가() {
        Long groupId = 1L;
        Long memberId = 10L;

        when(mapper.lockGroup(groupId)).thenReturn(groupId);
        when(mapper.findActiveOwnerName(groupId, memberId))
                .thenReturn("테스트회원");
        when(mapper.countCurrentAccount(groupId)).thenReturn(1);
        when(mapper.countPendingChange(groupId)).thenReturn(1);

        assertThrows(IllegalStateException.class, () ->
                service.requestAccountChange(
                        groupId,
                        memberId,
                        new GroupAccountChangeRequest(
                                "국민은행",
                                "123456789012"
                        )
                )
        );
    }

@Test
void 이미_등록된_계좌번호면_변경신청_불가() {
    Long groupId = 1L;
    Long memberId = 10L;

    GroupAccountChangeRequest request =
            new GroupAccountChangeRequest(
                    "국민은행",
                    "123456789012"
            );

    when(mapper.lockGroup(groupId))
            .thenReturn(groupId);

    when(mapper.findActiveOwnerName(groupId, memberId))
            .thenReturn("테스트회원");

    when(mapper.countCurrentAccount(groupId))
            .thenReturn(1);

    when(mapper.countPendingChange(groupId))
            .thenReturn(0);

    when(crypto.hashAccountNumber("123456789012"))
            .thenReturn("existing-hash");

    when(mapper.countDuplicateAccount("existing-hash"))
            .thenReturn(1);

    IllegalStateException exception =
            assertThrows(
                    IllegalStateException.class,
                    () -> service.requestAccountChange(
                            groupId,
                            memberId,
                            request
                    )
            );

    assertEquals(
            "이미 등록된 계좌입니다.",
            exception.getMessage()
    );

    verify(mapper, never()).nextAccountId();
    verify(mapper, never()).insertPendingAccount(
            anyLong(), anyLong(), anyString(),
            anyString(), anyString(), anyString(),
            anyString(), anyInt(), anyLong()
    );
}

@Test
void 감사로그_저장실패시_예외발생() {
    Long groupId = 1L;
    Long memberId = 10L;

    GroupAccountChangeRequest request =
            new GroupAccountChangeRequest(
                    "국민은행",
                    "123456789012"
            );

    when(mapper.lockGroup(groupId))
            .thenReturn(groupId);

    when(mapper.findActiveOwnerName(groupId, memberId))
            .thenReturn("테스트회원");

    when(mapper.countCurrentAccount(groupId))
            .thenReturn(1);

    when(mapper.countPendingChange(groupId))
            .thenReturn(0);

    when(crypto.hashAccountNumber(anyString()))
            .thenReturn("new-hash");

    when(mapper.countDuplicateAccount("new-hash"))
            .thenReturn(0);

    when(crypto.encrypt(anyString()))
            .thenReturn("encrypted");

    when(crypto.keyVersion())
            .thenReturn(1);

    when(mapper.nextAccountId())
            .thenReturn(100L);

    when(mapper.insertPendingAccount(
            anyLong(), anyLong(), anyString(),
            anyString(), anyString(), anyString(),
            anyString(), anyInt(), anyLong()
    )).thenReturn(1);

    // 감사 로그 INSERT 실패 상황
    when(mapper.insertChangeAudit(
            anyLong(), anyLong(), anyString()
    )).thenReturn(0);

    IllegalStateException exception =
            assertThrows(
                    IllegalStateException.class,
                    () -> service.requestAccountChange(
                            groupId,
                            memberId,
                            request
                    )
            );

    assertEquals(
            "계좌 변경 신청 감사 로그 저장에 실패했습니다.",
            exception.getMessage()
    );

    verify(mapper).insertPendingAccount(
            anyLong(), anyLong(), anyString(),
            anyString(), anyString(), anyString(),
            anyString(), anyInt(), anyLong()
    );

    verify(mapper).insertChangeAudit(
            eq(groupId),
            eq(memberId),
            anyString()
    );
}


@Test
void 회원동의현황_정상조회() {
    Long groupId = 1L;
    Long memberId = 10L;
    Long accountId = 100L;

    when(mapper.countActiveGroupMember(groupId, memberId))
            .thenReturn(1);
    when(mapper.findPendingAccountId(groupId))
            .thenReturn(accountId);
    when(mapper.findConsentTargetInfo(
        groupId,
        accountId,
        memberId
        )).thenReturn(
                new GroupAccountConsentTargetInfo(
                        "TEST_BANK",
                        "*****1234",
                        null
                )
        );
    when(mapper.countActiveGroupMembers(groupId))
            .thenReturn(4);
    when(mapper.countAgreedMembers(groupId, accountId))
            .thenReturn(2);
    when(mapper.countRejectedMembers(groupId, accountId))
            .thenReturn(1);

    GroupAccountConsentStatusResponse result =
            service.getConsentStatus(groupId, memberId);

    assertEquals(4, result.totalMembers());
    assertEquals(2, result.agreedMembers());
    assertEquals(1, result.rejectedMembers());
    assertEquals(1, result.pendingMembers());
    assertFalse(result.allAgreed());
    assertNull(result.myAgreedYn());
    assertTrue(result.canRespond());
    assertEquals("TEST_BANK", result.bankName());
    assertEquals("*****1234", result.accountMasked());
}

@Test
void 진행중인_계좌변경신청이_없으면_404() {
    Long groupId = 1L;
    Long memberId = 10L;

    when(mapper.countActiveGroupMember(groupId, memberId))
            .thenReturn(1);
    when(mapper.findPendingAccountId(groupId))
            .thenReturn(null);

    ResponseStatusException exception =
            assertThrows(
                    ResponseStatusException.class,
                    () -> service.getConsentStatus(
                            groupId, memberId
                    )
            );

    assertEquals(404, exception.getStatusCode().value());
}


@Test
void 회원_계좌변경_동의성공() {
    when(mapper.lockGroup(1L)).thenReturn(1L);
    when(mapper.countActiveGroupMember(1L, 10L)).thenReturn(1);
    when(mapper.findPendingAccountId(1L)).thenReturn(100L);
    when(mapper.countExistingAccountConsent(1L, 100L, 10L))
            .thenReturn(0);
    when(mapper.insertAccountConsent(1L, 100L, 10L, "Y"))
            .thenReturn(1);
    when(mapper.insertConsentAudit(
            eq(1L), eq(10L), anyString()
    )).thenReturn(1);

    GroupAccountConsentResponse result =
            service.respondToConsent(
                    1L, 100L, 10L,
                    new GroupAccountConsentRequest(true)
            );

    assertEquals("Y", result.consentStatus());
    verify(mapper).insertAccountConsent(1L, 100L, 10L, "Y");
}

@Test
void 회원_계좌변경_거절성공() {
    when(mapper.lockGroup(1L)).thenReturn(1L);
    when(mapper.countActiveGroupMember(1L, 10L)).thenReturn(1);
    when(mapper.findPendingAccountId(1L)).thenReturn(100L);
    when(mapper.insertAccountConsent(1L, 100L, 10L, "N"))
            .thenReturn(1);
    when(mapper.insertConsentAudit(
            eq(1L), eq(10L), anyString()
    )).thenReturn(1);
    when(mapper.retirePendingAccount(1L, 100L))
        .thenReturn(1);

    GroupAccountConsentResponse result =
            service.respondToConsent(
                    1L, 100L, 10L,
                    new GroupAccountConsentRequest(false)
            );

    assertEquals("N", result.consentStatus());
    verify(mapper).insertAccountConsent(1L, 100L, 10L, "N");
    verify(mapper).retirePendingAccount(1L, 100L);
}

@Test
void 이미_응답한_회원은_중복동의_불가() {
    when(mapper.lockGroup(1L)).thenReturn(1L);
    when(mapper.countActiveGroupMember(1L, 10L)).thenReturn(1);
    when(mapper.findPendingAccountId(1L)).thenReturn(100L);
    when(mapper.countExistingAccountConsent(1L, 100L, 10L))
            .thenReturn(1);

    assertThrows(IllegalStateException.class, () ->
            service.respondToConsent(
                    1L, 100L, 10L,
                    new GroupAccountConsentRequest(true)
            )
    );

    verify(mapper, never()).insertAccountConsent(
            anyLong(), anyLong(), anyLong(), anyString()
    );
}

@Test
void 다른_신청ID에는_동의불가() {
    when(mapper.lockGroup(1L)).thenReturn(1L);
    when(mapper.countActiveGroupMember(1L, 10L)).thenReturn(1);
    when(mapper.findPendingAccountId(1L)).thenReturn(100L);

    assertThrows(IllegalStateException.class, () ->
            service.respondToConsent(
                    1L, 999L, 10L,
                    new GroupAccountConsentRequest(true)
            )
    );

    verify(mapper, never()).insertAccountConsent(
            anyLong(), anyLong(), anyLong(), anyString()
    );
}


@Test
void 모임장_계좌변경_신청취소_성공() {
    when(mapper.lockGroup(1L)).thenReturn(1L);
    when(mapper.findActiveOwnerName(1L, 10L))
            .thenReturn("모임장");
    when(mapper.findPendingAccountId(1L))
            .thenReturn(100L);
    when(mapper.retirePendingAccount(1L, 100L))
            .thenReturn(1);
    when(mapper.insertChangeAudit(
            eq(1L), eq(10L), anyString()
    )).thenReturn(1);

    GroupAccountChangeCloseResponse result =
            service.cancelChange(1L, 100L, 10L);

    assertEquals("CANCELLED", result.requestStatus());
    verify(mapper).retirePendingAccount(1L, 100L);
}

@Test
void 모임장이_아니면_신청취소_불가() {
    when(mapper.lockGroup(1L)).thenReturn(1L);
    when(mapper.findActiveOwnerName(1L, 20L))
            .thenReturn(null);

    assertThrows(SecurityException.class, () ->
            service.cancelChange(1L, 100L, 20L)
    );

    verify(mapper, never()).retirePendingAccount(
            anyLong(), anyLong()
    );
}

@Test
void 회원_거절시_변경신청_종료() {
    when(mapper.lockGroup(1L)).thenReturn(1L);
    when(mapper.countActiveGroupMember(1L, 10L))
            .thenReturn(1);
    when(mapper.findPendingAccountId(1L))
            .thenReturn(100L);
    when(mapper.insertAccountConsent(
            1L, 100L, 10L, "N"
    )).thenReturn(1);
    when(mapper.retirePendingAccount(1L, 100L))
            .thenReturn(1);
    when(mapper.insertConsentAudit(
            eq(1L), eq(10L), anyString()
    )).thenReturn(1);

    GroupAccountConsentResponse result =
            service.respondToConsent(
                    1L,
                    100L,
                    10L,
                    new GroupAccountConsentRequest(false)
            );

    assertEquals("N", result.consentStatus());
    verify(mapper).retirePendingAccount(1L, 100L);
}


@Test
void 전원동의와_인증완료여도_이체전에는_확정불가() {
    when(mapper.findActiveOwnerName(1L, 10L))
            .thenReturn("모임장");

    when(mapper.findPendingVerificationTarget(1L, 100L))
            .thenReturn(new GroupAccountVerificationTarget(
                    100L, 1L, 10L,
                    "테스트은행", "encrypted",
                    "9012", 1,
                    "VERIFIED", "PENDING"
            ));

    when(mapper.findPendingAccountId(1L))
            .thenReturn(100L);

    when(mapper.countActiveGroupMembers(1L))
            .thenReturn(4);

    when(mapper.countAgreedMembers(1L, 100L))
            .thenReturn(4);

    when(mapper.countRejectedMembers(1L, 100L))
            .thenReturn(0);

    GroupAccountChangeReadinessResponse result =
            service.checkReadiness(1L, 100L, 10L);

    assertTrue(result.consentReady());
    assertTrue(result.ownershipVerified());
    assertFalse(result.mockTransferReady());
    assertFalse(result.finalizeReady());
}

@Test
void 신규회원_미동의면_전원동의_불충족() {
    when(mapper.findActiveOwnerName(1L, 10L))
            .thenReturn("모임장");

    when(mapper.findPendingVerificationTarget(1L, 100L))
            .thenReturn(new GroupAccountVerificationTarget(
                    100L, 1L, 10L,
                    "테스트은행", "encrypted",
                    "9012", 1,
                    "VERIFIED", "PENDING"
            ));

    when(mapper.findPendingAccountId(1L))
            .thenReturn(100L);

    // 기존 회원 4명 모두 동의했지만
    // 신규 회원 1명이 가입한 상황
    when(mapper.countActiveGroupMembers(1L))
            .thenReturn(5);

    when(mapper.countAgreedMembers(1L, 100L))
            .thenReturn(4);

    when(mapper.countRejectedMembers(1L, 100L))
            .thenReturn(0);

    GroupAccountChangeReadinessResponse result =
            service.checkReadiness(1L, 100L, 10L);

    assertEquals(5, result.totalMembers());
    assertEquals(4, result.agreedMembers());
    assertEquals(1, result.pendingMembers());
    assertFalse(result.consentReady());
    assertFalse(result.finalizeReady());
}

}
