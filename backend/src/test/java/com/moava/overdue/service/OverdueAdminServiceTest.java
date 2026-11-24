package com.moava.overdue.service;

import com.moava.common.util.NameMasker;
import com.moava.notification.common.domain.NotifType;
import com.moava.notification.common.service.NotificationService;
import com.moava.overdue.dto.OverdueActionRequest;
import com.moava.overdue.dto.OverdueCaseDto;
import com.moava.overdue.mapper.OverdueMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OverdueAdminService 단위 테스트 (F26 관리자 미납 목록 A06)
 */
@ExtendWith(MockitoExtension.class)
class OverdueAdminServiceTest {

    @Mock
    NotificationService notificationService;    // 가짜 알림 서비스
    @Mock
    OverdueMapper overdueMapper;

    @InjectMocks
    OverdueAdminService overdueAdminService;

    static final Long ADMIN_ID = 1L;

    @Test
    @DisplayName("목록: ACTIVE 관리자면 조회하고 회원 이름은 마스킹")
    void list_masksName() {
        when(overdueMapper.countActiveAdmin(ADMIN_ID)).thenReturn(1);
        OverdueCaseDto row = new OverdueCaseDto();
        row.setMemberName("홍길동");
        when(overdueMapper.findOverdueCases("OPEN")).thenReturn(new ArrayList<>(List.of(row)));

        List<OverdueCaseDto> res = overdueAdminService.getCases(ADMIN_ID, "OPEN");

        assertEquals(NameMasker.mask("홍길동"), res.get(0).getMemberName());
    }

    @Test
    @DisplayName("ACTIVE 관리자가 아니면 403, 조회 안 함")
    void notAdmin_403() {
        when(overdueMapper.countActiveAdmin(ADMIN_ID)).thenReturn(0);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> overdueAdminService.getCases(ADMIN_ID, "OPEN"));

        assertEquals(403, e.getStatusCode().value());
        verify(overdueMapper, never()).findOverdueCases(any());
    }

    @Test
    @DisplayName("status가 OPEN/RESOLVED/ALL이 아니면 400")
    void wrongStatus_400() {
        when(overdueMapper.countActiveAdmin(ADMIN_ID)).thenReturn(1);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> overdueAdminService.getCases(ADMIN_ID, "DETECTED"));

        assertEquals(400, e.getStatusCode().value());
        verify(overdueMapper, never()).findOverdueCases(any());
    }
    // ===================== 관리자 조치 (A06) =====================

    static final Long CASE_ID = 500L;
    static final Long GROUP_ID = 10L;
    static final Long MEMBER_ID = 20L;

    @Test
    @DisplayName("경고: DETECTED → WARNED, 감사 로그 OVERDUE_WARN(관리자 기록), SANCTION은 안 만듦")
    void warn_success() {
        givenAdmin();
        when(overdueMapper.findOverdueCaseById(CASE_ID)).thenReturn(overdueCase("DETECTED", "ALERT_RESTRICT"));
        when(overdueMapper.updateCaseWarned(CASE_ID)).thenReturn(1);

        overdueAdminService.applyAction(ADMIN_ID, CASE_ID, action("WARN", null));

        verify(overdueMapper).insertAdminAuditLog(eq(GROUP_ID), eq(ADMIN_ID), eq(MEMBER_ID), eq("OVERDUE_WARN"), any());
        verify(overdueMapper, never()).insertSanction(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("경고: 이미 경고한 건이면(조건부 UPDATE 0건) 409, 감사 로그 안 남김")
    void warn_twice_409() {
        givenAdmin();
        when(overdueMapper.findOverdueCaseById(CASE_ID)).thenReturn(overdueCase("WARNED", "ALERT_RESTRICT"));
        when(overdueMapper.updateCaseWarned(CASE_ID)).thenReturn(0);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> overdueAdminService.applyAction(ADMIN_ID, CASE_ID, action("WARN", null)));

        assertEquals(409, e.getStatusCode().value());
        verify(overdueMapper, never()).insertAdminAuditLog(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("채팅 제한: 회원 N→Y, 미납 건 RESTRICTED, SANCTION(CHAT_RESTRICT·사유), 회원 알림, 감사 로그")
    void restrict_success() {
        givenAdmin();
        when(overdueMapper.findOverdueCaseById(CASE_ID)).thenReturn(overdueCase("WARNED", "ALERT_RESTRICT"));
        when(overdueMapper.updateChatRestricted(GROUP_ID, MEMBER_ID, "N", "Y")).thenReturn(1);
        when(overdueMapper.nextSanctionId()).thenReturn(77L);

        overdueAdminService.applyAction(ADMIN_ID, CASE_ID, action("CHAT_RESTRICT", "  미납 2개월  "));

        verify(overdueMapper).updateCaseRestricted(CASE_ID);
        verify(overdueMapper).insertSanction(77L, "CHAT_RESTRICT", MEMBER_ID, GROUP_ID, ADMIN_ID, "미납 2개월");  // 앞뒤 공백 제거
        verify(notificationService).notifyMember(eq(MEMBER_ID), eq(NotifType.SANCTION), eq(77L), any());
        verify(overdueMapper).insertAdminAuditLog(eq(GROUP_ID), eq(ADMIN_ID), eq(MEMBER_ID), eq("CHAT_RESTRICT"), contains("#77"));
    }

    @Test
    @DisplayName("채팅 제한: 모임 규칙이 ALERT_ONLY(알림만)면 409, 아무것도 안 바꿈")
    void restrict_alertOnly_409() {
        givenAdmin();
        when(overdueMapper.findOverdueCaseById(CASE_ID)).thenReturn(overdueCase("DETECTED", "ALERT_ONLY"));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> overdueAdminService.applyAction(ADMIN_ID, CASE_ID, action("CHAT_RESTRICT", "미납")));

        assertEquals(409, e.getStatusCode().value());
        verify(overdueMapper, never()).updateChatRestricted(any(), any(), any(), any());
    }

    @Test
    @DisplayName("채팅 제한: 사유가 비어 있으면 400, 아무것도 안 바꿈")
    void restrict_noReason_400() {
        givenAdmin();
        when(overdueMapper.findOverdueCaseById(CASE_ID)).thenReturn(overdueCase("DETECTED", "ALERT_RESTRICT"));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> overdueAdminService.applyAction(ADMIN_ID, CASE_ID, action("CHAT_RESTRICT", "   ")));

        assertEquals(400, e.getStatusCode().value());
        verify(overdueMapper, never()).updateChatRestricted(any(), any(), any(), any());
    }

    @Test
    @DisplayName("해소된 건이라도 제한 해제는 가능: 회원 Y→N, SANCTION(CHAT_RELEASE), 회원 알림")
    void release_onResolvedCase_ok() {
        givenAdmin();
        when(overdueMapper.findOverdueCaseById(CASE_ID)).thenReturn(overdueCase("RESOLVED", "ALERT_RESTRICT"));
        when(overdueMapper.updateChatRestricted(GROUP_ID, MEMBER_ID, "Y", "N")).thenReturn(1);
        when(overdueMapper.nextSanctionId()).thenReturn(78L);

        overdueAdminService.applyAction(ADMIN_ID, CASE_ID, action("CHAT_RELEASE", "납입 확인"));

        verify(overdueMapper).insertSanction(78L, "CHAT_RELEASE", MEMBER_ID, GROUP_ID, ADMIN_ID, "납입 확인");
        verify(notificationService).notifyMember(eq(MEMBER_ID), eq(NotifType.SANCTION), eq(78L), any());
    }

    @Test
    @DisplayName("해소된 건에 경고하면 409")
    void resolvedCase_warn_409() {
        givenAdmin();
        when(overdueMapper.findOverdueCaseById(CASE_ID)).thenReturn(overdueCase("RESOLVED", "ALERT_RESTRICT"));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> overdueAdminService.applyAction(ADMIN_ID, CASE_ID, action("WARN", null)));

        assertEquals(409, e.getStatusCode().value());
        verify(overdueMapper, never()).updateCaseWarned(any());
    }

    @Test
    @DisplayName("후순위 검토 기록: SANCTION(ORDER_REVIEW)만 남기고 상태·채팅·알림은 그대로")
    void orderReview_recordOnly() {
        givenAdmin();
        when(overdueMapper.findOverdueCaseById(CASE_ID)).thenReturn(overdueCase("WARNED", "ALERT_ONLY"));
        when(overdueMapper.nextSanctionId()).thenReturn(79L);

        overdueAdminService.applyAction(ADMIN_ID, CASE_ID, action("ORDER_REVIEW", "3개월 연속 미납"));

        verify(overdueMapper).insertSanction(79L, "ORDER_REVIEW", MEMBER_ID, GROUP_ID, ADMIN_ID, "3개월 연속 미납");
        int i = verify(overdueMapper, never()).updateChatRestricted(any(), any(), any(), any());
        verify(notificationService, never()).notifyMember(anyLong(), any(), any(), any());
    }

    // ===================== 조치 테스트 도우미 =====================

    private void givenAdmin() {
        when(overdueMapper.countActiveAdmin(ADMIN_ID)).thenReturn(1);
    }

    private OverdueCaseDto overdueCase(String status, String overduePolicy) {
        OverdueCaseDto c = new OverdueCaseDto();
        c.setOverdueCaseId(CASE_ID);
        c.setGroupId(GROUP_ID);
        c.setGroupTitle("테스트모임");
        c.setMemberId(MEMBER_ID);
        c.setMemberName("홍길동");
        c.setPayMonth("202611");
        c.setStatus(status);
        c.setOverduePolicy(overduePolicy);
        return c;
    }

    private OverdueActionRequest action(String action, String reason) {
        OverdueActionRequest r = new OverdueActionRequest();
        r.setAction(action);
        r.setReason(reason);
        return r;
    }
}