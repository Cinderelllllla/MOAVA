package com.moava.notification.common.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.moava.member.service.PhoneCryptoService;
import com.moava.notification.common.dto.NotificationSmsEvent;
import com.moava.notification.common.mapper.NotificationSmsMapper;
import com.moava.notification.common.sms.SmsSender;

/**
 * F36 문자 발송. SMS 대상 알림(PAY_DUE_3D · RISK_UP · OVERDUE_WARNED)이 저장되면 실행된다.
 *
 * <p>AFTER_COMMIT : 업무가 롤백되면 문자도 나가지 않는다.
 * <p>@Async       : 발송이 느리거나 실패해도 본 요청에 영향 없음 (AsyncConfig).
 * <p>moava.sms.enabled=false(기본)면 로그만 남긴다 → 팀원 PC에서 실수로 문자가 나가지 않음.
 * <p>번호는 발송 직전에만 복호화하고, 로그에 남기지 않는다. 실패는 로그만 남기고 삼킨다.
 * <p>ACTIVE가 아닌 회원(탈퇴·익명화)과 번호 없는 회원은 건너뛴다.
 * <p>서비스 필수 알림이라 마케팅 수신 동의(MKT_SMS_OPT)와 무관하게 보낸다 (기능명세서 F36).
 */
@Component
public class NotificationSmsListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationSmsListener.class);
    static final String PREFIX = "[MOAVA] ";

    private final NotificationSmsMapper mapper;
    private final PhoneCryptoService phoneCrypto;
    private final SmsSender sender;
    private final boolean enabled;

    public NotificationSmsListener(NotificationSmsMapper mapper,
                                   PhoneCryptoService phoneCrypto,
                                   SmsSender sender,
                                   @Value("${moava.sms.enabled:false}") boolean enabled) {
        this.mapper = mapper;
        this.phoneCrypto = phoneCrypto;
        this.sender = sender;
        this.enabled = enabled;
    }

    /** 처리 결과 (테스트·로그용) */
    enum Result { DISABLED, NO_MEMBER, INACTIVE, NO_PHONE, SENT, FAILED }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSms(NotificationSmsEvent e) {
        Result r = handle(e);
        log.info("[SMS {}] notificationId={}, type={}", r, e.notificationId(), e.type());
    }

    Result handle(NotificationSmsEvent e) {
        if (!enabled || !sender.isConfigured()) {
            return Result.DISABLED;
        }
        try {
            Map<String, Object> target = mapper.findSmsTarget(e.memberId());
            if (target == null) return Result.NO_MEMBER;
            if (!"ACTIVE".equals(target.get("status"))) return Result.INACTIVE;

            Object enc = target.get("phoneEnc");
            if (enc == null || enc.toString().isBlank()) return Result.NO_PHONE;

            int keyVer = ((Number) target.get("keyVer")).intValue();
            String phone = phoneCrypto.decrypt(enc.toString(), keyVer);

            if (!sender.send(phone, PREFIX + e.body())) return Result.FAILED;
            mapper.markSmsSent(e.notificationId());
            return Result.SENT;
        } catch (Exception ex) {
            // 복호화·DB 오류도 업무에 영향 없이 삼킨다. 메시지에 번호가 섞일 수 있어 종류만 남김
            log.warn("[SMS] 처리 실패 notificationId={}: {}", e.notificationId(), ex.getClass().getSimpleName());
            return Result.FAILED;
        }
    }
}
