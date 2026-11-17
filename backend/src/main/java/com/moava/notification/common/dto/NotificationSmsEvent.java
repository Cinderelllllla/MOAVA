package com.moava.notification.common.dto;

import com.moava.notification.common.domain.NotifType;

/** SMS 대상 알림이 저장됐을 때 발행. 커밋 이후 NotificationSmsListener가 처리한다. */
public record NotificationSmsEvent(long notificationId, long memberId, NotifType type, String body) {
}
