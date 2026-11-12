package com.moava.notification.common.dto;

import java.util.List;

/** 선택 삭제(숨김) 요청: {"ids": [1, 2, 3]} */
public record NotificationHideRequest(List<Long> ids) {
}
