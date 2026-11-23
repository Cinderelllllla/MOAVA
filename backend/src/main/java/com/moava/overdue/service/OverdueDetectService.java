package com.moava.overdue.service;

import com.moava.common.util.NameMasker;
import com.moava.notification.common.domain.NotifType;
import com.moava.notification.common.service.NotificationService;
import com.moava.overdue.dto.OverdueTargetDto;
import com.moava.overdue.mapper.OverdueMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

/**
 * F26 미납 자동 감지 (매일 02:00 배치에서 호출)
 * - 미납 1일 차·5일 차: 회원에게 납입 안내(PAY_OVERDUE)
 * - 미납 5일 차 이상 + 아직 감지 안 된 건: OVERDUE_CASE 저장(DETECTED) → 관리자 전원 알림(OVERDUE_5D) → 감사 로그(OVERDUE_DETECT)
 * - 납입 확인(CONFIRMED) 시 해소(RESOLVED)는 F23 ProofReviewService에서 처리
 */
@Service
@RequiredArgsConstructor
public class OverdueDetectService {

    static final int FIRST_NOTICE_DAYS = 1;   // 미납 1일 차 회원 안내
    static final int ADMIN_ALERT_DAYS = 5;    // 미납 5일 차부터 관리자 알림 + 미납 건 저장 (팀 결정 11/04)

    private final OverdueMapper overdueMapper;
    private final NotificationService notificationService;

    /** today: 기준일 (배치는 오늘, 테스트는 원하는 날짜를 넣어 확인) */
    @Transactional
    public DetectResult detect(LocalDate today) {
        // 1. 회원 납입 안내 — 1일 차, 5일 차 두 번만 (알림 코드표: PAY_OVERDUE "미납 1일 차·5일 차")
        int memberNotices = 0;
        for (OverdueTargetDto t : overdueMapper.findUnpaidOnDay(today, FIRST_NOTICE_DAYS)) {
            notifyMember(t, "납입 후 증빙을 등록해 주세요.");
            memberNotices++;
        }
        for (OverdueTargetDto t : overdueMapper.findUnpaidOnDay(today, ADMIN_ALERT_DAYS)) {
            notifyMember(t, "관리자 확인 대상이 됐어요. 납입 후 증빙을 등록해 주세요.");
            memberNotices++;
        }

        // 2. 새 미납 건 — 저장 + 관리자 알림 + 감사 로그
        int newCases = 0;
        for (OverdueTargetDto t : overdueMapper.findNewOverdueTargets(today, ADMIN_ALERT_DAYS)) {
            try {
                overdueMapper.insertOverdueCase(t);
            } catch (DuplicateKeyException e) {
                continue;   // 배치가 겹쳐 돌아 이미 저장된 건 → 알림을 또 보내지 않음 (UNIQUE 모임·회원·월)
            }
            notificationService.notifyAdmins(NotifType.OVERDUE_5D, t.getOverdueCaseId(), Map.of(
                    "group", t.getGroupTitle(),
                    "member", NameMasker.mask(t.getMemberName()),   // 알림 규칙: 다른 회원 이름은 마스킹
                    "month", monthLabel(t.getPayMonth()),
                    "days", t.getElapsedDays()));
            overdueMapper.insertAuditLog(t.getGroupId(), t.getMemberId(), "OVERDUE_DETECT",
                    "미납 감지 #" + t.getOverdueCaseId() + " (" + t.getPayMonth() + ", " + t.getElapsedDays() + "일 차)");
            newCases++;
        }
        return new DetectResult(newCases, memberNotices);
    }

    /** 배치 결과 (로그용) */
    public record DetectResult(int newCases, int memberNotices) {}

    // ---------- 내부 도우미 ----------

    private void notifyMember(OverdueTargetDto t, String guide) {
        notificationService.notifyMember(t.getMemberId(), NotifType.PAY_OVERDUE, null, Map.of(
                "groupId", t.getGroupId(),
                "group", t.getGroupTitle(),
                "month", monthLabel(t.getPayMonth()),
                "days", t.getElapsedDays(),
                "guide", guide));
    }

    // "202611" → "11월분"
    private static String monthLabel(String payMonth) {
        return Integer.parseInt(payMonth.substring(4)) + "월분";
    }
}