package com.moava.group.payout.controller;

import com.moava.common.response.ApiResponse;
import com.moava.group.payout.service.PayoutReceiptException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;

/** F28 곗돈 수령 완료 표시 컨트롤러 전용 예외 변환 → {error:{code,message}, requestId}. */
@RestControllerAdvice(assignableTypes = PayoutReceiptController.class)
public class PayoutReceiptExceptionHandler {

    @ExceptionHandler(PayoutReceiptException.class)
    public ResponseEntity<ApiResponse.Error> handle(PayoutReceiptException e) {
        return error(e.getStatus(), e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse.Error> badRequest(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 값을 확인해 주세요.");
    }

    private static ResponseEntity<ApiResponse.Error> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(ApiResponse.Error.of(code, message, "req-" + UUID.randomUUID().toString().substring(0, 8)));
    }
}
