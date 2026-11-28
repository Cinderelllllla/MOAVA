package com.moava.group.create.service;

import com.moava.group.search.event.GroupSearchSyncEvent;
import com.moava.group.create.mapper.GroupJoinRequestMapper;
import com.moava.notification.common.domain.NotifType;
import com.moava.notification.common.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupRecruitmentCloseTest {

    private static final Long GROUP_ID = 10L;
    private static final Long OWNER_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final Long PENDING_MEMBER_ID = 3L;
    private static final Long REQUEST_ID = 100L;
    private static final Long PENDING_REQUEST_ID = 101L;

    @Mock
    private GroupJoinRequestMapper mapper;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private GroupJoinRequestService service;

    @BeforeEach
    void setUp() {
        service = new GroupJoinRequestService(mapper, notificationService, eventPublisher);
    }

    @Test
    @DisplayName("수동 마감: 6명 정원에서 절반(3명) 이상이면 ACTIVE 전환, 잔여 PENDING 자동 거절·알림, 감사 로그, ES 동기화")
    void closeRecruitment_succeedsAtHalfCapacityAndRejectsPendingRequests() {
        when(mapper.lockGroup(GROUP_ID)).thenReturn(groupRow("RECRUITING", 6));
        when(mapper.countCurrentMembers(GROUP_ID)).thenReturn(3);
        when(mapper.closeRecruitment(GROUP_ID)).thenReturn(1);
        when(mapper.findPendingRequestsForClose(GROUP_ID)).thenReturn(List.of(
                Map.of("JOIN_REQUEST_ID", PENDING_REQUEST_ID, "MEMBER_ID", PENDING_MEMBER_ID)
        ));
        when(mapper.rejectAllPendingRequestsOnClose(GROUP_ID, OWNER_ID)).thenReturn(1);
        when(mapper.insertAuditLog(eq(GROUP_ID), eq(OWNER_ID), isNull(), eq("RECRUIT_CLOSE"), anyString()))
                .thenReturn(1);

        Map<String, Object> result = service.closeRecruitment(GROUP_ID, OWNER_ID);

        assertEquals("ACTIVE", result.get("status"));
        assertEquals(3, result.get("currentMembers"));
        assertEquals(6, result.get("maxMembers"));
        assertEquals(1, result.get("rejectedPendingCount"));

        verify(mapper).closeRecruitment(GROUP_ID);
        verify(mapper).rejectAllPendingRequestsOnClose(GROUP_ID, OWNER_ID);
        verify(notificationService).notifyMember(
                eq(PENDING_MEMBER_ID),
                eq(NotifType.JOIN_RESULT),
                eq(PENDING_REQUEST_ID),
                eq(Map.of("group", "테스트 계모임", "result", "모집 마감으로 거절"))
        );
        verify(mapper).insertAuditLog(
                eq(GROUP_ID), eq(OWNER_ID), isNull(), eq("RECRUIT_CLOSE"),
                contains("3/6명"));
        verify(eventPublisher).publishEvent(any(GroupSearchSyncEvent.class));
    }

    @Test
    @DisplayName("수동 마감: 6명 정원에서 절반 미만(2명)이면 예외 발생")
    void closeRecruitment_rejectsBelowHalfCapacity() {
        when(mapper.lockGroup(GROUP_ID)).thenReturn(groupRow("RECRUITING", 6));
        when(mapper.countCurrentMembers(GROUP_ID)).thenReturn(2);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.closeRecruitment(GROUP_ID, OWNER_ID));

        assertEquals(true, ex.getMessage().contains("최소 3명"));
        verify(mapper, never()).closeRecruitment(anyLong());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("수동 마감: 모임장이 아니면 SecurityException")
    void closeRecruitment_rejectsNonOwner() {
        when(mapper.lockGroup(GROUP_ID)).thenReturn(groupRow("RECRUITING", 6));

        assertThrows(SecurityException.class,
                () -> service.closeRecruitment(GROUP_ID, MEMBER_ID));

        verify(mapper, never()).closeRecruitment(anyLong());
    }

    @Test
    @DisplayName("수동 마감: 이미 ACTIVE 상태면 IllegalStateException")
    void closeRecruitment_rejectsAlreadyActiveGroup() {
        when(mapper.lockGroup(GROUP_ID)).thenReturn(groupRow("ACTIVE", 6));

        assertThrows(IllegalStateException.class,
                () -> service.closeRecruitment(GROUP_ID, OWNER_ID));

        verify(mapper, never()).closeRecruitment(anyLong());
    }

    @Test
    @DisplayName("자동 마감: 가입 승인으로 정원 도달 시(2+1 >= 3) 자동으로 모집 마감 및 잔여 신청 거절")
    void approveJoinRequest_autoClosesWhenReachingMaxMembers() {
        when(mapper.lockGroup(GROUP_ID)).thenReturn(groupRow("RECRUITING", 3));
        when(mapper.findJoinRequestForDecision(GROUP_ID, REQUEST_ID)).thenReturn(pendingJoinRequest(MEMBER_ID));
        when(mapper.findMemberStatus(MEMBER_ID)).thenReturn(Map.of("STATUS", "ACTIVE", "BANNED_YN", "N"));
        when(mapper.countExistingMembership(GROUP_ID, MEMBER_ID)).thenReturn(0);
        when(mapper.countCurrentMembers(GROUP_ID)).thenReturn(2);
        when(mapper.approveJoinRequest(GROUP_ID, REQUEST_ID, OWNER_ID)).thenReturn(1);
        when(mapper.insertApprovedMember(GROUP_ID, MEMBER_ID)).thenReturn(1);
        when(mapper.insertAuditLog(eq(GROUP_ID), eq(OWNER_ID), any(), anyString(), anyString())).thenReturn(1);
        when(mapper.closeRecruitment(GROUP_ID)).thenReturn(1);
        when(mapper.findPendingRequestsForClose(GROUP_ID)).thenReturn(List.of(
                Map.of("JOIN_REQUEST_ID", PENDING_REQUEST_ID, "MEMBER_ID", PENDING_MEMBER_ID)
        ));
        when(mapper.rejectAllPendingRequestsOnClose(GROUP_ID, OWNER_ID)).thenReturn(1);

        service.approveJoinRequest(GROUP_ID, REQUEST_ID, OWNER_ID);

        verify(mapper).closeRecruitment(GROUP_ID);
        verify(mapper).rejectAllPendingRequestsOnClose(GROUP_ID, OWNER_ID);
        verify(notificationService).notifyMember(
                eq(PENDING_MEMBER_ID),
                eq(NotifType.JOIN_RESULT),
                eq(PENDING_REQUEST_ID),
                eq(Map.of("group", "테스트 계모임", "result", "모집 마감으로 거절"))
        );
        verify(mapper).insertAuditLog(
                eq(GROUP_ID), eq(OWNER_ID), isNull(), eq("RECRUIT_CLOSE"),
                contains("자동 마감"));
        verify(eventPublisher).publishEvent(any(GroupSearchSyncEvent.class));
    }

    @Test
    @DisplayName("자동 마감 미발생: 가입 승인 후에도 정원 미만(1+1 < 3)이면 모집 상태 유지")
    void approveJoinRequest_doesNotAutoCloseBeforeMaxMembers() {
        when(mapper.lockGroup(GROUP_ID)).thenReturn(groupRow("RECRUITING", 3));
        when(mapper.findJoinRequestForDecision(GROUP_ID, REQUEST_ID)).thenReturn(pendingJoinRequest(MEMBER_ID));
        when(mapper.findMemberStatus(MEMBER_ID)).thenReturn(Map.of("STATUS", "ACTIVE", "BANNED_YN", "N"));
        when(mapper.countExistingMembership(GROUP_ID, MEMBER_ID)).thenReturn(0);
        when(mapper.countCurrentMembers(GROUP_ID)).thenReturn(1);
        when(mapper.approveJoinRequest(GROUP_ID, REQUEST_ID, OWNER_ID)).thenReturn(1);
        when(mapper.insertApprovedMember(GROUP_ID, MEMBER_ID)).thenReturn(1);
        when(mapper.insertAuditLog(eq(GROUP_ID), eq(OWNER_ID), eq(MEMBER_ID), eq("JOIN_APPROVE"), anyString()))
                .thenReturn(1);

        service.approveJoinRequest(GROUP_ID, REQUEST_ID, OWNER_ID);

        verify(mapper, never()).closeRecruitment(anyLong());
        verify(mapper, never()).rejectAllPendingRequestsOnClose(anyLong(), anyLong());
        verify(eventPublisher).publishEvent(any(GroupSearchSyncEvent.class));
    }

    private static Map<String, Object> groupRow(String status, int maxMembers) {
        return Map.of(
                "GROUP_ID", GROUP_ID,
                "OWNER_ID", OWNER_ID,
                "TITLE", "테스트 계모임",
                "STATUS", status,
                "VISIBILITY", "PUBLIC",
                "MAX_MEMBERS", maxMembers
        );
    }

    private static Map<String, Object> pendingJoinRequest(Long memberId) {
        return Map.of(
                "JOIN_REQUEST_ID", REQUEST_ID,
                "GROUP_ID", GROUP_ID,
                "MEMBER_ID", memberId,
                "STATUS", "PENDING",
                "EXPIRED_YN", "N"
        );
    }
}
