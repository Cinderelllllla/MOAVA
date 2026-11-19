package com.moava.finance.service;

import org.springframework.http.HttpStatus;

/** 금융 분석(F07·F10) 입력 오류·없는 데이터. 컨트롤러 어드바이스가 공통 오류 형식으로 바꿔 준다. */
public class FinanceException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public FinanceException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
