package com.moava.overdue.service;

import com.moava.common.util.NameMasker;
import com.moava.notification.common.domain.NotifType;
import com.moava.notification.common.service.NotificationService;
import com.moava.overdue.dto.OverdueTargetDto;
import com.moava.overdue.mapper.OverdueMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * F26 새 미납 건 1건 등록 — 저장 + 관리자 알림 + 감사 로그를 한 트랜잭션으로 처리
 * - OverdueDetectService와 클래스를 나눈 이유: 같은 클래스 안에서 @Transactional 메서드를 부르면
 *   프록시를 거치지 않아 트랜잭션이 걸리지 않음 (Spring AOP 프록시 방식)
 * - 하나라도 실패하면 이 건만 롤백 → DB에 안 남으므로 다음 날 배치에서 다시 대상이 됨
 */
@Service
@RequiredArgsConstructor
public class OverdueCaseRegistrar {

    private final OverdueMapper overdueMapper;
    private final NotificationService notificationService;

    @Transactional
    public void register(OverdueTargetDto t) {
        overdueMapper.insertOverdueCase(t);   // 이미 저장된 건이면 DuplicateKeyException → 호출한 쪽에서 건너뜀 (UNIQUE 모임·회원·월)
        notificationService.notifyAdmins(NotifType.OVERDUE_5D, t.getOverdueCaseId(), Map.of(
                "group", t.getGroupTitle(),
                "member", NameMasker.mask(t.getMemberName()),   // 알림 규칙: 다른 회원 이름은 마스킹
                "month", OverdueDetectService.monthLabel(t.getPayMonth()),
                "days", t.getElapsedDays()));
        overdueMapper.insertAuditLog(t.getGroupId(), t.getMemberId(), "OVERDUE_DETECT",
                "미납 감지 #" + t.getOverdueCaseId() + " (" + t.getPayMonth() + ", " + t.getElapsedDays() + "일 차)");
    }
}