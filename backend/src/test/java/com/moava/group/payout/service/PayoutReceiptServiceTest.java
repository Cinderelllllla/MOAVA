package com.moava.group.payout.service;

import com.moava.group.payout.dto.PayoutReceiptActionResponse;
import com.moava.group.payout.dto.PayoutReceiptMemberRow;
import com.moava.group.payout.dto.PayoutReceiptSummaryResponse;
import com.moava.group.payout.dto.PayoutReceiptTarget;
import com.moava.group.payout.mapper.PayoutReceiptMapper;
import com.moava.notification.common.domain.NotifType;
import com.moava.notification.common.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F28 곗돈 수령 완료 표시 서비스 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class PayoutReceiptServiceTest {

    @Mock
    PayoutReceiptMapper mapper;

    @Mock
    NotificationService notifications;

    @InjectMocks
    PayoutReceiptService service;

    private static final long GROUP_ID = 10L;
    private static final long LEADER_ID = 1L;
    private static final long MEMBER_ID = 20L;

    private static PayoutReceiptTarget target(
            String groupStatus,
            String orderConfirmedYn,
            String memberStatus,
            String payoutReceivedYn,
            String payoutReceivedAt,
            Integer roundNo
    ) {
        return new PayoutReceiptTarget(
                GROUP_ID,
                "테스트 계모임",
                groupStatus,
                LEADER_ID,
                orderConfirmedYn,
                memberStatus,
                "홍길동",
                payoutReceivedYn,
                payoutReceivedAt,
                roundNo
        );
    }

    private static void assertError(PayoutReceiptException e, HttpStatus status, String code) {
        assertEquals(status, e.getStatus());
        assertEquals(code, e.getCode());
    }

    @Test
    @DisplayName("수령 완료 표시 성공: 순서 확정 모임에서 모임장이 표시 → GYE_MEMBER 갱신 + AUDIT_LOG(PAYOUT_RECEIVED) + NotificationService(PAYOUT_RECEIVED)")
    void markReceived_success() {
        when(mapper.lockGroup(GROUP_ID)).thenReturn(GROUP_ID);
        when(mapper.selectTarget(GROUP_ID, MEMBER_ID)).thenReturn(
                target("ACTIVE", "Y", "ACTIVE", "N", null, 2),
                target("ACTIVE", "Y", "ACTIVE", "Y", "2026-11-27 17:10", 2)
        );
        when(mapper.markPayoutReceived(GROUP_ID, MEMBER_ID)).thenReturn(1);

        PayoutReceiptActionResponse res = service.markReceived(LEADER_ID, GROUP_ID, MEMBER_ID);

        assertEquals(GROUP_ID, res.groupId());
        assertEquals(MEMBER_ID, res.memberId());
        assertEquals("홍○동", res.memberName());
        assertEquals(2, res.confirmedRoundNo());
        assertTrue(res.payoutReceived());
        assertEquals("2026-11-27 17:10", res.payoutReceivedAt());

        verify(mapper).lockGroup(GROUP_ID);
        verify(mapper).markPayoutReceived(GROUP_ID, MEMBER_ID);
        verify(mapper).insertAuditLog(
                eq(GROUP_ID),
                eq(LEADER_ID),
                eq(MEMBER_ID),
                eq("PAYOUT_RECEIVED"),
                contains("홍○동 (2회차)")
        );
        verify(notifications).notifyMember(
                eq(MEMBER_ID),
                eq(NotifType.PAYOUT_RECEIVED),
                eq(GROUP_ID),
                eq(Map.of(
                        "group", "테스트 계모임",
                        "round", " (2회차)",
                        "groupId", GROUP_ID
                ))
        );
    }

    @Test
    @DisplayName("수령 완료 표시 차단: 모임장이 아니면 403 LEADER_ONLY")
    void markReceived_nonLeaderForbidden() {
        when(mapper.lockGroup(GROUP_ID)).thenReturn(GROUP_ID);
        when(mapper.selectTarget(GROUP_ID, MEMBER_ID))
                .thenReturn(target("ACTIVE", "Y", "ACTIVE", "N", null, 2));

        PayoutReceiptException e = assertThrows(
                PayoutReceiptException.class,
                () -> service.markReceived(999L, GROUP_ID, MEMBER_ID)
        );

        assertError(e, HttpStatus.FORBIDDEN, "LEADER_ONLY");
        verify(mapper, never()).markPayoutReceived(anyLong(), anyLong());
        verify(mapper, never()).insertAuditLog(anyLong(), anyLong(), anyLong(), anyString(), anyString());
        verify(notifications, never()).notifyMember(anyLong(), any(), any(), anyMap());
    }

    @Test
    @DisplayName("수령 완료 표시 차단: 수령 순서 미확정 모임(ORDER_CONFIRMED_YN='N')은 409 ORDER_NOT_CONFIRMED")
    void markReceived_orderNotConfirmed() {
        when(mapper.lockGroup(GROUP_ID)).thenReturn(GROUP_ID);
        when(mapper.selectTarget(GROUP_ID, MEMBER_ID))
                .thenReturn(target("RECRUITING", "N", "ACTIVE", "N", null, null));

        PayoutReceiptException e = assertThrows(
                PayoutReceiptException.class,
                () -> service.markReceived(LEADER_ID, GROUP_ID, MEMBER_ID)
        );

        assertError(e, HttpStatus.CONFLICT, "ORDER_NOT_CONFIRMED");
        verify(mapper, never()).markPayoutReceived(anyLong(), anyLong());
    }

    @Test
    @DisplayName("수령 완료 표시 차단: 해산된 모임은 409 GROUP_DISSOLVED")
    void markReceived_dissolvedGroup() {
        when(mapper.lockGroup(GROUP_ID)).thenReturn(GROUP_ID);
        when(mapper.selectTarget(GROUP_ID, MEMBER_ID))
                .thenReturn(target("DISSOLVED", "Y", "ACTIVE", "N", null, 2));

        PayoutReceiptException e = assertThrows(
                PayoutReceiptException.class,
                () -> service.markReceived(LEADER_ID, GROUP_ID, MEMBER_ID)
        );

        assertError(e, HttpStatus.CONFLICT, "GROUP_DISSOLVED");
        verify(mapper, never()).markPayoutReceived(anyLong(), anyLong());
    }

    @Test
    @DisplayName("수령 완료 표시 차단: 이미 수령 완료된 회원은 409 ALREADY_RECEIVED")
    void markReceived_alreadyReceived() {
        when(mapper.lockGroup(GROUP_ID)).thenReturn(GROUP_ID);
        when(mapper.selectTarget(GROUP_ID, MEMBER_ID))
                .thenReturn(target("ACTIVE", "Y", "ACTIVE", "Y", "2026-11-26 12:00", 2));

        PayoutReceiptException e = assertThrows(
                PayoutReceiptException.class,
                () -> service.markReceived(LEADER_ID, GROUP_ID, MEMBER_ID)
        );

        assertError(e, HttpStatus.CONFLICT, "ALREADY_RECEIVED");
        verify(mapper, never()).markPayoutReceived(anyLong(), anyLong());
    }

    @Test
    @DisplayName("수령 완료 표시 차단: 동시 요청으로 UPDATE 0행이면 409 ALREADY_RECEIVED")
    void markReceived_concurrentConflict() {
        when(mapper.lockGroup(GROUP_ID)).thenReturn(GROUP_ID);
        when(mapper.selectTarget(GROUP_ID, MEMBER_ID))
                .thenReturn(target("ACTIVE", "Y", "ACTIVE", "N", null, 2));
        when(mapper.markPayoutReceived(GROUP_ID, MEMBER_ID)).thenReturn(0);

        PayoutReceiptException e = assertThrows(
                PayoutReceiptException.class,
                () -> service.markReceived(LEADER_ID, GROUP_ID, MEMBER_ID)
        );

        assertError(e, HttpStatus.CONFLICT, "ALREADY_RECEIVED");
        verify(mapper, never()).insertAuditLog(anyLong(), anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("수령 완료 표시 차단: 참여 중(ACTIVE)이 아닌 회원은 404 NOT_GROUP_MEMBER")
    void markReceived_notActiveMember() {
        when(mapper.lockGroup(GROUP_ID)).thenReturn(GROUP_ID);
        when(mapper.selectTarget(GROUP_ID, MEMBER_ID))
                .thenReturn(target("ACTIVE", "Y", "WITHDRAWN", "N", null, null));

        PayoutReceiptException e = assertThrows(
                PayoutReceiptException.class,
                () -> service.markReceived(LEADER_ID, GROUP_ID, MEMBER_ID)
        );

        assertError(e, HttpStatus.NOT_FOUND, "NOT_GROUP_MEMBER");
    }

    @Test
    @DisplayName("현황 조회 성공: 일반 회원이 본인 수령 완료 여부(myPayoutReceived) 및 타인 마스킹 이름을 확인한다")
    void getSummary_memberSuccess() {
        when(mapper.selectTarget(GROUP_ID, MEMBER_ID))
                .thenReturn(target("ACTIVE", "Y", "ACTIVE", "Y", "2026-11-27 17:10", 2));
        when(mapper.selectMemberReceipts(GROUP_ID)).thenReturn(List.of(
                new PayoutReceiptMemberRow(LEADER_ID, "김모임", "OWNER", 1, "N", null),
                new PayoutReceiptMemberRow(MEMBER_ID, "홍길동", "MEMBER", 2, "Y", "2026-11-27 17:10")
        ));

        PayoutReceiptSummaryResponse res = service.getSummary(MEMBER_ID, GROUP_ID);

        assertTrue(res.orderConfirmed());
        assertFalse(res.leader());
        assertTrue(res.myPayoutReceived());
        assertEquals("2026-11-27 17:10", res.myPayoutReceivedAt());
        assertEquals(2, res.members().size());
        assertEquals("김○임", res.members().get(0).memberName());
        assertFalse(res.members().get(0).self());
        assertEquals("홍길동", res.members().get(1).memberName());
        assertTrue(res.members().get(1).self());
    }

    @Test
    @DisplayName("현황 조회 차단: 모임 회원이 아니면 403 NOT_GROUP_MEMBER")
    void getSummary_nonMemberForbidden() {
        when(mapper.selectTarget(GROUP_ID, 999L))
                .thenReturn(target("ACTIVE", "Y", null, null, null, null));

        PayoutReceiptException e = assertThrows(
                PayoutReceiptException.class,
                () -> service.getSummary(999L, GROUP_ID)
        );

        assertError(e, HttpStatus.FORBIDDEN, "NOT_GROUP_MEMBER");
    }
}
