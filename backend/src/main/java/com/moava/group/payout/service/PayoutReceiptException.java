package com.moava.group.payout.service;

import org.springframework.http.HttpStatus;

/** F28 곗돈 수령 완료 표시 전용 예외 (HTTP 상태 + 코드 + 한국어 안내 메시지). */
public class PayoutReceiptException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public PayoutReceiptException(HttpStatus status, String code, String message) {
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
