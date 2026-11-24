package com.moava.overdue.service;

import com.moava.common.util.NameMasker;
import com.moava.notification.common.domain.NotifType;
import com.moava.notification.common.service.NotificationService;
import com.moava.overdue.dto.OverdueTargetDto;
import com.moava.overdue.mapper.OverdueMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OverdueDetectService 단위 테스트 (F26 미납 자동 감지) — DB 없이 가짜 Mapper·알림 서비스(Mock)로 처리 흐름만 확인
 * 감지 쿼리 조건(시작일·가입일·운영 기간)은 SQL이라 여기서는 확인하지 않음 → DB에서 따로 확인
 */
@ExtendWith(MockitoExtension.class)
class OverdueDetectServiceTest {

    @Mock
    OverdueMapper overdueMapper;                    // 가짜 Mapper (DB 대신)

    @Mock
    NotificationService notificationService;        // 가짜 알림 서비스 (실제 알림 저장 안 함)

    @InjectMocks
    OverdueDetectService overdueDetectService;      // 위 가짜들을 넣어서 만든 진짜 Service

    static final LocalDate TODAY = LocalDate.of(2026, 11, 10);   // 납입일 5일 모임 기준 11월분 5일 차
    static final Long GROUP_ID = 10L;

    @Test
    @DisplayName("새 미납 건: OVERDUE_CASE 저장 → 관리자 알림(OVERDUE_5D, 회원 이름 마스킹) → 감사 로그 OVERDUE_DETECT")
    void newCase_savesAndNotifiesAdmins() {
        when(overdueMapper.findNewOverdueTargets(TODAY, 5)).thenReturn(List.of(target(20L, "홍길동", 5)));
        doAnswer(inv -> {                           // 실제 DB처럼 저장하면서 미납 건 ID를 채워 줌 (selectKey 흉내)
            ((OverdueTargetDto) inv.getArgument(0)).setOverdueCaseId(500L);
            return 1;
        }).when(overdueMapper).insertOverdueCase(any());

        OverdueDetectService.DetectResult r = overdueDetectService.detect(TODAY);

        assertEquals(1, r.newCases());
        verify(notificationService).notifyAdmins(eq(NotifType.OVERDUE_5D), eq(500L), eq(Map.of(
                "group", "테스트모임",
                "member", NameMasker.mask("홍길동"),
                "month", "11월분",
                "days", 5)));
        verify(overdueMapper).insertAuditLog(eq(GROUP_ID), eq(20L), eq("OVERDUE_DETECT"), contains("#500"));
    }

    @Test
    @DisplayName("같은 건이 이미 저장돼 있으면(UNIQUE 위반) 관리자 알림·감사 로그를 다시 남기지 않음")
    void duplicateCase_noSecondAlert() {
        when(overdueMapper.findNewOverdueTargets(TODAY, 5)).thenReturn(List.of(target(20L, "홍길동", 5)));
        when(overdueMapper.insertOverdueCase(any())).thenThrow(new DuplicateKeyException("UK_OVERDUE_CASE_1"));

        OverdueDetectService.DetectResult r = overdueDetectService.detect(TODAY);

        assertEquals(0, r.newCases());
        verify(notificationService, never()).notifyAdmins(any(), any(), any());
        verify(overdueMapper, never()).insertAuditLog(any(), any(), any(), any());
    }

    @Test
    @DisplayName("미납 1일 차·5일 차 회원에게 납입 안내(PAY_OVERDUE), 새 미납 건이 없으면 관리자 알림 없음")
    void memberNotice_day1AndDay5() {
        when(overdueMapper.findUnpaidOnDay(TODAY, 1)).thenReturn(List.of(target(21L, "김일일", 1)));
        when(overdueMapper.findUnpaidOnDay(TODAY, 5)).thenReturn(List.of(target(22L, "박오일", 5)));

        OverdueDetectService.DetectResult r = overdueDetectService.detect(TODAY);

        assertEquals(2, r.memberNotices());
        assertEquals(0, r.newCases());
        verify(notificationService).notifyMember(eq(21L), eq(NotifType.PAY_OVERDUE), isNull(),
                argThat(p -> Integer.valueOf(1).equals(p.get("days"))
                        && GROUP_ID.equals(p.get("groupId"))       // 알림 클릭 시 /evidence?groupId= 로 이동
                        && "11월분".equals(p.get("month"))));
        verify(notificationService).notifyMember(eq(22L), eq(NotifType.PAY_OVERDUE), isNull(),
                argThat(p -> Integer.valueOf(5).equals(p.get("days"))));
        verify(notificationService, never()).notifyAdmins(any(), any(), any());
    }

    // ===================== 도우미 =====================
    @Test
    @DisplayName("납입일 3일 전 회원에게 납입 안내(PAY_DUE_3D): 납입일은 'M월 D일', 약정액은 천 단위 쉼표")
    void dueNotice_3DaysBefore() {
        OverdueTargetDto t = target(23L, "이삼일", -3);
        t.setAmount(100000L);
        when(overdueMapper.findDueNoticeTargets(TODAY, 3)).thenReturn(List.of(t));

        OverdueDetectService.DetectResult r = overdueDetectService.detect(TODAY);

        assertEquals(1, r.dueNotices());
        assertEquals(0, r.memberNotices());      // 미납 안내(PAY_OVERDUE)와 따로 셈
        verify(notificationService).notifyMember(eq(23L), eq(NotifType.PAY_DUE_3D), isNull(), eq(Map.of(
                "groupId", GROUP_ID,
                "group", "테스트모임",
                "month", "11월분",
                "payDate", "11월 5일",
                "amount", "100,000")));
    }

    // 감지 쿼리 결과 1행 흉내 (같은 모임 · 11월분 · 납부 기한 11/05)
    private OverdueTargetDto target(Long memberId, String name, int elapsedDays) {
        OverdueTargetDto t = new OverdueTargetDto();
        t.setGroupId(GROUP_ID);
        t.setGroupTitle("테스트모임");
        t.setMemberId(memberId);
        t.setMemberName(name);
        t.setPayMonth("202611");
        t.setDueDate(LocalDate.of(2026, 11, 5));
        t.setElapsedDays(elapsedDays);
        return t;
    }
}