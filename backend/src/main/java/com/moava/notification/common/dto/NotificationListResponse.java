package com.moava.notification.common.dto;

import java.util.List;

/** 알림함 목록 응답. hasNext가 true면 다음 page를 더 불러올 수 있다. */
public record NotificationListResponse(List<NotificationItem> items, int page, int size, boolean hasNext) {
}
