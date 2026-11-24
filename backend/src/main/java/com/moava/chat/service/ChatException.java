package com.moava.chat.service;

import org.springframework.http.HttpStatus;

/** 채팅(F34) 권한·입력 오류. REST 는 ChatExceptionHandler, 웹소켓은 /user/queue/errors 로 전달된다. */
public class ChatException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ChatException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
