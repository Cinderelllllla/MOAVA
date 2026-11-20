package com.moava.notification.common.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moava.notification.common.mapper.NotificationCleanupMapper;

/**
 * 오래된 알림 정리 배치 (알림 운영 정책).
 * - 읽은 알림: 만든 지 30일 지나면 숨김
 * - 안 읽은 알림: 만든 지 90일 지나면 숨김
 * 실제 삭제가 아니라 DELETED_YN='Y'라서 운영 감사 이력은 DB에 남는다.
 * 처리해야 할 일(동의·증빙 등)은 각 화면 배너에 계속 보이므로 알림을 숨겨도 업무가 사라지지 않는다.
 *
 * 실행 시각: moava.notification.cleanup.cron (기본 매일 03:30, 한국 시간)
 * 끄기: moava.notification.cleanup.enabled=false
 * @Scheduled 활성화는 finance/config/FinanceSchedulingConfig 의 @EnableScheduling 을 함께 쓴다.
 */
@Service
public class NotificationCleanupService {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanupService.class);

    static final int READ_RETENTION_DAYS = 14;
    static final int UNREAD_RETENTION_DAYS = 30;

    private final NotificationCleanupMapper mapper;
    private final boolean enabled;

    public NotificationCleanupService(NotificationCleanupMapper mapper,
                                      @Value("${moava.notification.cleanup.enabled:true}") boolean enabled) {
        this.mapper = mapper;
        this.enabled = enabled;
    }

    /** 정리 결과 (숨긴 건수) */
    public record CleanupResult(int hiddenRead, int hiddenUnread) {
        public int total() { return hiddenRead + hiddenUnread; }
    }

    @Scheduled(cron = "${moava.notification.cleanup.cron:0 30 3 * * *}", zone = "Asia/Seoul")
    @Transactional
    public CleanupResult cleanup() {
        if (!enabled) {
            log.debug("[알림 정리] moava.notification.cleanup.enabled=false 라서 건너뜀");
            return new CleanupResult(0, 0);
        }
        int read = mapper.hideOldRead(READ_RETENTION_DAYS);
        int unread = mapper.hideOldUnread(UNREAD_RETENTION_DAYS);
        CleanupResult result = new CleanupResult(read, unread);
        log.info("[알림 정리] 읽음 {}일 경과 {}건, 안 읽음 {}일 경과 {}건 숨김",
                READ_RETENTION_DAYS, read, UNREAD_RETENTION_DAYS, unread);
        return result;
    }
}
