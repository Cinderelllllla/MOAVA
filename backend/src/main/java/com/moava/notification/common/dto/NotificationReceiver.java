package com.moava.notification.common.dto;

/**
 * 알림을 받는 사람. 회원이면 memberId만, 관리자면 adminId만 채운다 (CK_NOTIFICATION_RECEIVER와 같은 규칙).
 */
public record NotificationReceiver(Long memberId, Long adminId) {

    public NotificationReceiver {
        if ((memberId == null) == (adminId == null)) {
            throw new IllegalArgumentException("memberId와 adminId 중 정확히 하나만 있어야 합니다.");
        }
    }

    public static NotificationReceiver member(long memberId) {
        return new NotificationReceiver(memberId, null);
    }

    public static NotificationReceiver admin(long adminId) {
        return new NotificationReceiver(null, adminId);
    }
}
