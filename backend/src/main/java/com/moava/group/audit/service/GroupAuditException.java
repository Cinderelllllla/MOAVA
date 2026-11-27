package com.moava.group.audit.service;

import org.springframework.http.HttpStatus;

/** F33 회원용 모임 운영 기록(감사 로그) 전용 예외. */
public class GroupAuditException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public GroupAuditException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
