package com.moava.group.warning.service;

import com.moava.group.warning.dto.GroupWarningRequest;
import com.moava.group.warning.dto.GroupWarningResponse;
import com.moava.group.warning.dto.GroupWarningTarget;
import com.moava.group.warning.mapper.GroupWarningMapper;
import com.moava.notification.common.domain.NotifType;
import com.moava.notification.common.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GroupWarningService 단위 테스트 (F30 모임 경고 및 강제 탈퇴, MOA-75)
 * Mapper 는 가짜(Mock)라 SQL 자체는 검증하지 않는다 → 실제 쿼리는 개인 DB 호출로 따로 확인.
 */
@ExtendWith(MockitoExtension.class)
class GroupWarningServiceTest {

    @Mock
    GroupWarningMapper mapper;
    @Mock
    NotificationService notifications;

    @InjectMocks
    GroupWarningService service;

    static final long GROUP_ID = 10L;
    static final long LEADER_ID = 1L;
    static final long TARGET_ID = 20L;
    static final GroupWarningRequest REQ = new GroupWarningRequest("  모임 규칙 위반  ");

    /** 모임장 LEADER_ID 인 운영 중 모임 + 대상 회원 상태·경고 횟수 */
    static GroupWarningTarget target(String memberStatus, Integer warningCount) {
        return new GroupWarningTarget(GROUP_ID, "테스트 모임", "ACTIVE", LEADER_ID, memberStatus, warningCount);
    }

    static void assertError(GroupWarningException e, HttpStatus status, String code) {
        assertEquals(status, e.getStatus());
        assertEquals(code, e.getCode());
    }

    // ===================== 경고 =====================

    @Test
    @DisplayName("경고: 0회 → 1회, SANCTION(WARN)·AUDIT_LOG(WARN)·알림, 사유는 앞뒤 공백 제거")
    void warn_first() {
        when(mapper.selectTarget(GROUP_ID, TARGET_ID))
                .thenReturn(target("ACTIVE", 0), target("ACTIVE", 1));   // 증가 전 → 증가 후
        when(mapper.increaseWarning(GROUP_ID, TARGET_ID)).thenReturn(1);

        GroupWarningResponse res = service.warn(LEADER_ID, GROUP_ID, TARGET_ID, REQ);

        assertEquals(1, res.warningCount());
        assertEquals("ACTIVE", res.memberStatus());
        assertFalse(res.expelReviewable());
        verify(mapper).insertSanction("WARN", TARGET_ID, GROUP_ID, LEADER_ID, "모임 규칙 위반");
        verify(mapper).insertAuditLog(eq(GROUP_ID), eq(LEADER_ID), eq(TARGET_ID), eq("WARN"), anyString());
        verify(notifications).notifyMember(eq(TARGET_ID), eq(NotifType.SANCTION), eq(GROUP_ID), anyMap());
    }

    @Test
    @DisplayName("경고: 1회 → 2회가 되면 강제 탈퇴 검토 대상, 자동 탈퇴는 하지 않음")
    void warn_second_reviewable() {
        when(mapper.selectTarget(GROUP_ID, TARGET_ID))
                .thenReturn(target("ACTIVE", 1), target("ACTIVE", 2));
        when(mapper.increaseWarning(GROUP_ID, TARGET_ID)).thenReturn(1);

        GroupWarningResponse res = service.warn(LEADER_ID, GROUP_ID, TARGET_ID, REQ);

        assertEquals(2, res.warningCount());
        assertEquals("ACTIVE", res.memberStatus());
        assertTrue(res.expelReviewable());
        verify(mapper, never()).expel(anyLong(), anyLong());
    }

    @Test
    @DisplayName("경고: 이미 2회면 409 WARNING_LIMIT, 아무것도 기록하지 않음")
    void warn_limit() {
        when(mapper.selectTarget(GROUP_ID, TARGET_ID)).thenReturn(target("ACTIVE", 2));

        GroupWarningException e = assertThrows(GroupWarningException.class,
                () -> service.warn(LEADER_ID, GROUP_ID, TARGET_ID, REQ));

        assertError(e, HttpStatus.CONFLICT, "WARNING_LIMIT");
        verify(mapper, never()).increaseWarning(anyLong(), anyLong());
        verify(mapper, never()).insertSanction(any(), anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("경고: 동시 요청으로 UPDATE 0행이면 409 WARNING_LIMIT")
    void warn_concurrent() {
        when(mapper.selectTarget(GROUP_ID, TARGET_ID)).thenReturn(target("ACTIVE", 1));
        when(mapper.increaseWarning(GROUP_ID, TARGET_ID)).thenReturn(0);

        GroupWarningException e = assertThrows(GroupWarningException.class,
                () -> service.warn(LEADER_ID, GROUP_ID, TARGET_ID, REQ));

        assertError(e, HttpStatus.CONFLICT, "WARNING_LIMIT");
        verify(mapper, never()).insertSanction(any(), anyLong(), anyLong(), anyLong(), any());
        verify(notifications, never()).notifyMember(anyLong(), any(), any(), anyMap());
    }

    // ===================== 공통 검증 =====================

    @Test
    @DisplayName("모임이 없으면 404 GROUP_NOT_FOUND")
    void groupNotFound() {
        when(mapper.selectTarget(GROUP_ID, TARGET_ID)).thenReturn(null);

        GroupWarningException e = assertThrows(GroupWarningException.class,
                () -> service.warn(LEADER_ID, GROUP_ID, TARGET_ID, REQ));

        assertError(e, HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND");
    }

    @Test
    @DisplayName("모임장이 아니면 403 LEADER_ONLY")
    void notLeader() {
        when(mapper.selectTarget(GROUP_ID, TARGET_ID)).thenReturn(target("ACTIVE", 0));

        GroupWarningException e = assertThrows(GroupWarningException.class,
                () -> service.warn(99L, GROUP_ID, TARGET_ID, REQ));

        assertError(e, HttpStatus.FORBIDDEN, "LEADER_ONLY");
        verify(mapper, never()).increaseWarning(anyLong(), anyLong());
    }

    @Test
    @DisplayName("해산된 모임이면 409 GROUP_DISSOLVED")
    void dissolved() {
        when(mapper.selectTarget(GROUP_ID, TARGET_ID)).thenReturn(
                new GroupWarningTarget(GROUP_ID, "테스트 모임", "DISSOLVED", LEADER_ID, "ACTIVE", 0));

        GroupWarningException e = assertThrows(GroupWarningException.class,
                () -> service.warn(LEADER_ID, GROUP_ID, TARGET_ID, REQ));

        assertError(e, HttpStatus.CONFLICT, "GROUP_DISSOLVED");
    }

    @Test
    @DisplayName("모임장 본인을 대상으로 하면 400 CANNOT_TARGET_LEADER")
    void targetLeader() {
        when(mapper.selectTarget(GROUP_ID, LEADER_ID)).thenReturn(target("ACTIVE", 0));

        GroupWarningException e = assertThrows(GroupWarningException.class,
                () -> service.warn(LEADER_ID, GROUP_ID, LEADER_ID, REQ));

        assertError(e, HttpStatus.BAD_REQUEST, "CANNOT_TARGET_LEADER");
    }

    @Test
    @DisplayName("대상이 이 모임 회원이 아니거나(null) 탈퇴 회원이면 404 NOT_GROUP_MEMBER")
    void notActiveMember() {
        when(mapper.selectTarget(GROUP_ID, TARGET_ID))
                .thenReturn(target(null, null), target("WITHDRAWN", 0));

        GroupWarningException notJoined = assertThrows(GroupWarningException.class,
                () -> service.warn(LEADER_ID, GROUP_ID, TARGET_ID, REQ));
        GroupWarningException withdrawn = assertThrows(GroupWarningException.class,
                () -> service.warn(LEADER_ID, GROUP_ID, TARGET_ID, REQ));

        assertError(notJoined, HttpStatus.NOT_FOUND, "NOT_GROUP_MEMBER");
        assertError(withdrawn, HttpStatus.NOT_FOUND, "NOT_GROUP_MEMBER");
    }

    @Test
    @DisplayName("사유: 비었거나 공백뿐이거나 본문이 없으면 400")
    void reason_blank() {
        assertError(assertThrows(GroupWarningException.class,
                () -> GroupWarningService.validReason(null)), HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        assertError(assertThrows(GroupWarningException.class,
                () -> GroupWarningService.validReason(new GroupWarningRequest("   "))), HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    }

    @Test
    @DisplayName("사유: 한글 166자(498바이트)는 통과, 167자(501바이트)는 400")
    void reason_bytes() {
        assertEquals(166, GroupWarningService.validReason(new GroupWarningRequest("가".repeat(166))).length());
        assertError(assertThrows(GroupWarningException.class,
                        () -> GroupWarningService.validReason(new GroupWarningRequest("가".repeat(167)))),
                HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    }

    // ===================== 강제 탈퇴 =====================

    @Test
    @DisplayName("강제 탈퇴: 경고 2회면 EXPELLED, SANCTION(EXPEL)·AUDIT_LOG(EXPEL)·알림, 모임 행 잠금 먼저")
    void expel_ok() {
        when(mapper.selectTarget(GROUP_ID, TARGET_ID)).thenReturn(target("ACTIVE", 2));
        when(mapper.countActiveWithdrawal(GROUP_ID, TARGET_ID)).thenReturn(0);
        when(mapper.expel(GROUP_ID, TARGET_ID)).thenReturn(1);

        GroupWarningResponse res = service.expel(LEADER_ID, GROUP_ID, TARGET_ID, REQ);

        assertEquals("EXPELLED", res.memberStatus());
        verify(mapper).lockGroup(GROUP_ID);
        verify(mapper).insertSanction("EXPEL", TARGET_ID, GROUP_ID, LEADER_ID, "모임 규칙 위반");
        verify(mapper).insertAuditLog(eq(GROUP_ID), eq(LEADER_ID), eq(TARGET_ID), eq("EXPEL"), anyString());
        verify(notifications).notifyMember(eq(TARGET_ID), eq(NotifType.SANCTION), eq(GROUP_ID), anyMap());
    }

    @Test
    @DisplayName("강제 탈퇴: 경고 2회 미만이면 409 NOT_ENOUGH_WARNINGS, 상태 변경 없음")
    void expel_notEnough() {
        when(mapper.selectTarget(GROUP_ID, TARGET_ID)).thenReturn(target("ACTIVE", 1));

        GroupWarningException e = assertThrows(GroupWarningException.class,
                () -> service.expel(LEADER_ID, GROUP_ID, TARGET_ID, REQ));

        assertError(e, HttpStatus.CONFLICT, "NOT_ENOUGH_WARNINGS");
        verify(mapper, never()).expel(anyLong(), anyLong());
    }

    @Test
    @DisplayName("강제 탈퇴: 중도 탈퇴 진행 중이면 409 WITHDRAWAL_IN_PROGRESS")
    void expel_withdrawalInProgress() {
        when(mapper.selectTarget(GROUP_ID, TARGET_ID)).thenReturn(target("ACTIVE", 2));
        when(mapper.countActiveWithdrawal(GROUP_ID, TARGET_ID)).thenReturn(1);

        GroupWarningException e = assertThrows(GroupWarningException.class,
                () -> service.expel(LEADER_ID, GROUP_ID, TARGET_ID, REQ));

        assertError(e, HttpStatus.CONFLICT, "WITHDRAWAL_IN_PROGRESS");
        verify(mapper, never()).expel(anyLong(), anyLong());
        verify(mapper, never()).insertSanction(any(), anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("강제 탈퇴: UPDATE 0행(그사이 상태 변경)이면 409, 기록·알림 없음")
    void expel_updateZero() {
        when(mapper.selectTarget(GROUP_ID, TARGET_ID)).thenReturn(target("ACTIVE", 2));
        when(mapper.countActiveWithdrawal(GROUP_ID, TARGET_ID)).thenReturn(0);
        when(mapper.expel(GROUP_ID, TARGET_ID)).thenReturn(0);

        GroupWarningException e = assertThrows(GroupWarningException.class,
                () -> service.expel(LEADER_ID, GROUP_ID, TARGET_ID, REQ));

        assertError(e, HttpStatus.CONFLICT, "NOT_ENOUGH_WARNINGS");
        verify(mapper, never()).insertSanction(any(), anyLong(), anyLong(), anyLong(), any());
        verify(notifications, never()).notifyMember(anyLong(), any(), any(), anyMap());
    }

    // ===================== 나의 누적 경고 =====================

    @Test
    @DisplayName("나의 누적 경고: ACTIVE 회원 본인의 횟수, 2회면 검토 대상")
    void myWarnings_member() {
        when(mapper.selectTarget(GROUP_ID, TARGET_ID)).thenReturn(target("ACTIVE", 2));

        GroupWarningResponse res = service.myWarnings(TARGET_ID, GROUP_ID);

        assertEquals(2, res.warningCount());
        assertTrue(res.expelReviewable());
    }

    @Test
    @DisplayName("나의 누적 경고: 이 모임 회원이 아니면 404 NOT_GROUP_MEMBER")
    void myWarnings_notMember() {
        when(mapper.selectTarget(GROUP_ID, TARGET_ID)).thenReturn(target(null, null));

        GroupWarningException e = assertThrows(GroupWarningException.class,
                () -> service.myWarnings(TARGET_ID, GROUP_ID));

        assertError(e, HttpStatus.NOT_FOUND, "NOT_GROUP_MEMBER");
    }
}
