package com.moava.finance.controller;

import com.moava.common.response.ApiResponse;
import com.moava.finance.service.FinanceException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/** 금융(F07·F10) 컨트롤러 전용 오류 변환. 다른 패키지 컨트롤러에는 영향을 주지 않는다. */
@RestControllerAdvice(assignableTypes = {FinanceMonthlyController.class, SpendingDefaultController.class})
public class FinanceExceptionHandler {

    @ExceptionHandler(FinanceException.class)
    public ResponseEntity<ApiResponse.Error> handle(FinanceException e) {
        return ResponseEntity.status(e.getStatus())
                .body(ApiResponse.Error.of(e.getCode(), e.getMessage(), "req-" + UUID.randomUUID().toString().substring(0, 8)));
    }
}
