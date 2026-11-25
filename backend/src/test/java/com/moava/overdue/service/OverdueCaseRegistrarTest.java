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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OverdueCaseRegistrar 단위 테스트 (F26 새 미납 건 1건 등록) — 저장 → 관리자 알림 → 감사 로그 순서와 내용 확인
 * 트랜잭션 롤백 자체는 Spring이 처리하므로 여기서는 "중간에 실패하면 예외가 밖으로 나가는지"까지만 확인
 */
@ExtendWith(MockitoExtension.class)
class OverdueCaseRegistrarTest {

    @Mock
    OverdueMapper overdueMapper;

    @Mock
    NotificationService notificationService;

    @InjectMocks
    OverdueCaseRegistrar overdueCaseRegistrar;

    static final Long GROUP_ID = 10L;

    @Test
    @DisplayName("OVERDUE_CASE 저장 → 관리자 알림(OVERDUE_5D, 회원 이름 마스킹) → 감사 로그 OVERDUE_DETECT")
    void register_savesNotifiesAudits() {
        OverdueTargetDto t = target();
        doAnswer(inv -> {                           // 실제 DB처럼 저장하면서 미납 건 ID를 채워 줌 (selectKey 흉내)
            ((OverdueTargetDto) inv.getArgument(0)).setOverdueCaseId(500L);
            return 1;
        }).when(overdueMapper).insertOverdueCase(any());

        overdueCaseRegistrar.register(t);

        verify(notificationService).notifyAdmins(eq(NotifType.OVERDUE_5D), eq(500L), eq(Map.of(
                "group", "테스트모임",
                "member", NameMasker.mask("홍길동"),
                "month", "11월분",
                "days", 5)));
        verify(overdueMapper).insertAuditLog(eq(GROUP_ID), eq(20L), eq("OVERDUE_DETECT"), contains("#500"));
    }

    @Test
    @DisplayName("이미 저장된 건이면(UNIQUE 위반) 예외를 밖으로 던지고 알림·감사 로그는 안 남김")
    void register_duplicate_throwsWithoutAlert() {
        when(overdueMapper.insertOverdueCase(any())).thenThrow(new DuplicateKeyException("UK_OVERDUE_CASE_1"));

        assertThrows(DuplicateKeyException.class, () -> overdueCaseRegistrar.register(target()));

        verify(notificationService, never()).notifyAdmins(any(), any(), any());
        verify(overdueMapper, never()).insertAuditLog(any(), any(), any(), any());
    }

    // 감지 쿼리 결과 1행 흉내 (11월분 · 납부 기한 11/05 · 5일 차)
    private OverdueTargetDto target() {
        OverdueTargetDto t = new OverdueTargetDto();
        t.setGroupId(GROUP_ID);
        t.setGroupTitle("테스트모임");
        t.setMemberId(20L);
        t.setMemberName("홍길동");
        t.setPayMonth("202611");
        t.setDueDate(LocalDate.of(2026, 11, 5));
        t.setElapsedDays(5);
        return t;
    }
}