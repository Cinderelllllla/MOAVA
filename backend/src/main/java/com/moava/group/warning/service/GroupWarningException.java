package com.moava.group.warning.service;

import org.springframework.http.HttpStatus;

/** F30 경고·강제 탈퇴 권한·입력·상태 오류. GroupWarningExceptionHandler 가 {error:{code,message}} 로 바꾼다. */
public class GroupWarningException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public GroupWarningException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}