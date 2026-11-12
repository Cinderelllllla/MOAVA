package com.moava.notification.common.service;

import com.moava.notification.common.dto.NotificationSmsEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * F36 SMS 발송 자리 (P2 · 2차). 지금은 로그만 남긴다.
 *
 * <p>AFTER_COMMIT : 업무가 롤백되면 문자도 나가지 않는다.
 * <p>@Async       : 발송이 느리거나 실패해도 본 요청에 영향 없음. (@EnableAsync 필요)
 */
@Component
public class NotificationSmsListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationSmsListener.class);

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSms(NotificationSmsEvent e) {
        // TODO(2차): MEMBER.PHONE_ENC 복호화 → SOLAPI 호출 → 성공 시 SMS_SENT_YN='Y'
        //            전화번호는 로그에 남기지 않는다. 실패는 로그만 남기고 삼킨다.
        log.info("[SMS 대기] notificationId={}, type={}", e.notificationId(), e.type());
    }
}
