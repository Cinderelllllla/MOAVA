package com.moava.group.rules.service;

import org.springframework.http.HttpStatus;

/** F31 운영 규칙 권한·입력 오류. GroupRulesExceptionHandler 가 {error:{code,message}} 로 바꾼다. */
public class GroupRulesException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public GroupRulesException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
