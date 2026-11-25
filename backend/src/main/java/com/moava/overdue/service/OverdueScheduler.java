package com.moava.overdue.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * F26 매일 02:00 미납 감지 배치
 * - @EnableScheduling은 finance/config/FinanceSchedulingConfig 설정을 함께 씀 (알림 정리 배치와 같은 방식)
 */
@Component
@RequiredArgsConstructor
public class OverdueScheduler {

    private static final Logger log = LoggerFactory.getLogger(OverdueScheduler.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final OverdueDetectService overdueDetectService;

    @Scheduled(cron = "${moava.overdue.detect.cron:0 0 2 * * *}", zone = "Asia/Seoul")
    public void run() {
        LocalDate today = LocalDate.now(SEOUL);
        try {
            OverdueDetectService.DetectResult r = overdueDetectService.detect(today);
            log.info("[F26] 미납 감지 완료 {} — 새 미납 {}건, 회원 안내 {}건", today, r.newCases(), r.memberNotices());
        } catch (Exception e) {
            log.error("[F26] 미납 감지 실패 {}", today, e);
        }
    }
}