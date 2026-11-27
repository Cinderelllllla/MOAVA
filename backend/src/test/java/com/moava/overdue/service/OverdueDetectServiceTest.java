package com.moava.overdue.service;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OverdueDetectService 단위 테스트 (F26 미납 자동 감지) — DB 없이 가짜 Mapper·알림·등록기(Mock)로 처리 흐름만 확인
 * 감지 쿼리 조건(시작일·가입일·운영 기간)은 SQL이라 여기서는 확인하지 않음 → DB에서 따로 확인
 * 미납 건 저장·관리자 알림·감사 로그 내용은 OverdueCaseRegistrarTest에서 확인
 */
@ExtendWith(MockitoExtension.class)
class OverdueDetectServiceTest {

    @Mock
    OverdueMapper overdueMapper;                    // 가짜 Mapper (DB 대신)

    @Mock
    NotificationService notificationService;        // 가짜 알림 서비스 (실제 알림 저장 안 함)

    @Mock
    OverdueCaseRegistrar overdueCaseRegistrar;      // 가짜 미납 건 등록기

    @InjectMocks
    OverdueDetectService overdueDetectService;      // 위 가짜들을 넣어서 만든 진짜 Service

    static final LocalDate TODAY = LocalDate.of(2026, 11, 10);   // 납입일 5일 모임 기준 11월분 5일 차
    static final Long GROUP_ID = 10L;

    // ===================== 새 미납 건 =====================

    @Test
    @DisplayName("새 미납 건은 한 건씩 등록기(OverdueCaseRegistrar)에 넘김")
    void newCase_delegatesToRegistrar() {
        OverdueTargetDto t = target(20L, "홍길동", 5);
        when(overdueMapper.findNewOverdueTargets(TODAY, 5)).thenReturn(List.of(t));

        OverdueDetectService.DetectResult r = overdueDetectService.detect(TODAY);

        assertEquals(1, r.newCases());
        assertEquals(0, r.failures());
        verify(overdueCaseRegistrar).register(t);
    }

    @Test
    @DisplayName("같은 건이 이미 저장돼 있으면(UNIQUE 위반) 건너뜀 — 실패로 세지 않음")
    void duplicateCase_skipped() {
        when(overdueMapper.findNewOverdueTargets(TODAY, 5)).thenReturn(List.of(target(20L, "홍길동", 5)));
        doThrow(new DuplicateKeyException("UK_OVERDUE_CASE_1")).when(overdueCaseRegistrar).register(any());

        OverdueDetectService.DetectResult r = overdueDetectService.detect(TODAY);

        assertEquals(0, r.newCases());
        assertEquals(0, r.failures());
    }

    @Test
    @DisplayName("미납 건 하나가 실패해도 다음 건은 계속 등록 (실패 1건으로 셈)")
    void registerFails_nextCaseContinues() {
        OverdueTargetDto fail = target(20L, "홍길동", 5);
        OverdueTargetDto ok = target(21L, "김철수", 5);
        when(overdueMapper.findNewOverdueTargets(TODAY, 5)).thenReturn(List.of(fail, ok));
        doThrow(new IllegalStateException("알림 저장 실패")).when(overdueCaseRegistrar).register(fail);

        OverdueDetectService.DetectResult r = overdueDetectService.detect(TODAY);

        assertEquals(1, r.newCases());
        assertEquals(1, r.failures());
        verify(overdueCaseRegistrar).register(ok);
    }

    // ===================== 회원 안내 =====================

    @Test
    @DisplayName("미납 1일 차·5일 차 회원에게 납입 안내(PAY_OVERDUE)")
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
        verify(overdueCaseRegistrar, never()).register(any());
    }

    @Test
    @DisplayName("한 회원의 안내가 실패해도 다음 회원에게는 계속 보냄 (실패 1건으로 셈)")
    void memberNoticeFails_nextMemberContinues() {
        when(overdueMapper.findUnpaidOnDay(TODAY, 1))
                .thenReturn(List.of(target(21L, "김일일", 1), target(24L, "최일일", 1)));
        when(notificationService.notifyMember(eq(21L), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("알림 값 누락"));

        OverdueDetectService.DetectResult r = overdueDetectService.detect(TODAY);

        assertEquals(1, r.memberNotices());
        assertEquals(1, r.failures());
        verify(notificationService).notifyMember(eq(24L), eq(NotifType.PAY_OVERDUE), isNull(), any());
    }

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

    // ===================== 도우미 =====================

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