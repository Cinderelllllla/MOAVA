package com.moava.notification.common.dto;

import lombok.Getter;
import lombok.Setter;

/** NOTIFICATION INSERT용 파라미터. notificationId는 selectKey로 채워진다. */
@Getter
@Setter
public class NotificationInsert {

    private Long notificationId;
    private Long memberId;
    private String notifType;
    private String title;
    private String body;
    private String linkPage;
    private Long refId;
}
