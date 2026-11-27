package com.moava.overdue.service;

import com.moava.notification.common.domain.NotifType;
import com.moava.notification.common.service.NotificationService;
import com.moava.overdue.dto.OverdueTargetDto;
import com.moava.overdue.mapper.OverdueMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

/**
 * F26 미납 자동 감지 (매일 02:00 배치에서 호출)
 * - 납입일 3일 전: 그 달 증빙을 아직 안 올린 회원에게 납입 안내(PAY_DUE_3D) — 알림 담당자와 합의해 미납 배치에서 함께 처리
 * - 미납 1일 차·5일 차: 회원에게 납입 안내(PAY_OVERDUE)
 * - 미납 5일 차 이상 + 아직 감지 안 된 건: OverdueCaseRegistrar가 저장(DETECTED)·관리자 알림(OVERDUE_5D)·감사 로그를 한 건씩 처리
 * - 납입 확인(CONFIRMED) 시 해소(RESOLVED)는 F23 ProofReviewService에서 처리
 * 트랜잭션: 배치 전체를 하나로 묶지 않음 — 대상 한 건이 실패해도 나머지는 진행하고, 실패한 건은 로그로 남김
 *
 */
@Service
@RequiredArgsConstructor
public class OverdueDetectService {

    private static final Logger log = LoggerFactory.getLogger(OverdueDetectService.class);

    static final int DUE_NOTICE_DAYS_BEFORE = 3;   // 납입일 3일 전 안내 (알림 코드표 PAY_DUE_3D)
    static final int FIRST_NOTICE_DAYS = 1;        // 미납 1일 차 회원 안내
    static final int ADMIN_ALERT_DAYS = 5;         // 미납 5일 차부터 관리자 알림 + 미납 건 저장 (팀 결정 11/04)

    private final OverdueMapper overdueMapper;
    private final NotificationService notificationService;
    private final OverdueCaseRegistrar overdueCaseRegistrar;

    /** today: 기준일 (배치는 오늘, 테스트는 원하는 날짜를 넣어 확인) */
    public DetectResult detect(LocalDate today) {
        int failures = 0;

        // 0. 납입일 3일 전 안내 — 그 달 증빙을 아직 안 올린 회원만 (승인·대기 제외)
        int dueNotices = 0;
        for (OverdueTargetDto t : overdueMapper.findDueNoticeTargets(today, DUE_NOTICE_DAYS_BEFORE)) {
            if (tryRun("납입 3일 전 안내", t, () -> notifyDue(t))) dueNotices++; else failures++;
        }

        // 1. 미납 안내 — 1일 차, 5일 차 두 번만 (알림 코드표: PAY_OVERDUE "미납 1일 차·5일 차")
        int memberNotices = 0;
        for (OverdueTargetDto t : overdueMapper.findUnpaidOnDay(today, FIRST_NOTICE_DAYS)) {
            if (tryRun("미납 1일 차 안내", t, () -> notifyOverdue(t, "납입 후 증빙을 등록해 주세요."))) memberNotices++; else failures++;
        }
        for (OverdueTargetDto t : overdueMapper.findUnpaidOnDay(today, ADMIN_ALERT_DAYS)) {
            if (tryRun("미납 5일 차 안내", t, () -> notifyOverdue(t, "관리자 확인 대상이 됐어요. 납입 후 증빙을 등록해 주세요."))) memberNotices++; else failures++;
        }

        // 2. 새 미납 건 — 한 건씩 별도 트랜잭션 (OverdueCaseRegistrar)
        int newCases = 0;
        for (OverdueTargetDto t : overdueMapper.findNewOverdueTargets(today, ADMIN_ALERT_DAYS)) {
            try {
                overdueCaseRegistrar.register(t);
                newCases++;
            } catch (DuplicateKeyException e) {
                // 배치가 겹쳐 돌아 이미 저장된 건 → 알림을 또 보내지 않음
            } catch (RuntimeException e) {
                failures++;
                logFailure("미납 건 등록", t, e);   // 롤백돼서 다음 날 배치에서 다시 대상이 됨
            }
        }
        return new DetectResult(newCases, memberNotices, dueNotices, failures);
    }

    /** 배치 결과 (로그용) */
    public record DetectResult(int newCases, int memberNotices, int dueNotices, int failures) {}

    // ---------- 내부 도우미 ----------

    // 한 건 실행 — 실패해도 예외를 밖으로 던지지 않고 로그만 남김 (다음 대상 계속 진행)
    private boolean tryRun(String job, OverdueTargetDto t, Runnable action) {
        try {
            action.run();
            return true;
        } catch (RuntimeException e) {
            logFailure(job, t, e);
            return false;
        }
    }

    // 로그에는 이름 대신 ID만 (개인정보 보호)
    // error: ID + 예외 종류만 / debug: 예외 메시지·스택 트레이스 (DB 값이 메시지에 섞일 수 있어서 분리)
    private static void logFailure(String job, OverdueTargetDto t, RuntimeException e) {
        log.error("[F26] {} 실패 — 모임 {} · 회원 {} · {} · {}",
                job, t.getGroupId(), t.getMemberId(), t.getPayMonth(), e.getClass().getSimpleName());
        log.debug("[F26] {} 실패 상세 — 모임 {} · 회원 {}", job, t.getGroupId(), t.getMemberId(), e);
    }

    private void notifyDue(OverdueTargetDto t) {
        notificationService.notifyMember(t.getMemberId(), NotifType.PAY_DUE_3D, null, Map.of(
                "groupId", t.getGroupId(),
                "group", t.getGroupTitle(),
                "month", monthLabel(t.getPayMonth()),
                "payDate", payDateLabel(t.getDueDate()),
                "amount", String.format("%,d", t.getAmount())));   // 100000 → "100,000"
    }

    private void notifyOverdue(OverdueTargetDto t, String guide) {
        notificationService.notifyMember(t.getMemberId(), NotifType.PAY_OVERDUE, null, Map.of(
                "groupId", t.getGroupId(),
                "group", t.getGroupTitle(),
                "month", monthLabel(t.getPayMonth()),
                "days", t.getElapsedDays(),
                "guide", guide));
    }

    // "202611" → "11월분" (OverdueCaseRegistrar에서도 사용)
    static String monthLabel(String payMonth) {
        return Integer.parseInt(payMonth.substring(4)) + "월분";
    }

    // 2026-12-01 → "12월 1일"
    private static String payDateLabel(LocalDate date) {
        return date.getMonthValue() + "월 " + date.getDayOfMonth() + "일";
    }
}