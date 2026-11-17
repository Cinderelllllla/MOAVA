package com.moava.notification.common.service;

import org.springframework.http.HttpStatus;

/** 알림함 API 오류. 컨트롤러가 API 명세서 §2 오류 형식으로 바꿔서 내려준다. */
public class NotificationException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public NotificationException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }

    public static NotificationException badRequest(String message) {
        return new NotificationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    public static NotificationException notFound() {
        return new NotificationException(HttpStatus.NOT_FOUND, "NOT_FOUND", "알림을 찾을 수 없습니다.");
    }

    public static NotificationException unauthorized() {
        return new NotificationException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다.");
    }
}
