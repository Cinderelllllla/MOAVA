package com.moava.notification.common.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 알림함 목록 한 줄. 프론트가 쓰는 id·title·body·read 이름을 그대로 맞춘다.
 */
@Getter
@Setter
public class NotificationItem {

    private Long id;
    /** NOTIF_TYPE (NotifType 이름) */
    private String type;
    private String title;
    private String body;
    /** 클릭 시 이동할 프론트 경로 (예: /procedures?groupId=12) */
    private String linkPage;
    private Long refId;
    private boolean read;
    private LocalDateTime createdAt;
}
