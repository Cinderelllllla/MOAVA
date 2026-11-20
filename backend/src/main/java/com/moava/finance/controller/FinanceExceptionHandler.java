package com.moava.finance.controller;

import com.moava.common.response.ApiResponse;
import com.moava.finance.service.FinanceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;

/** 금융(F07·F10) 컨트롤러 전용 오류 변환. 다른 패키지 컨트롤러에는 영향을 주지 않는다. */
@RestControllerAdvice(assignableTypes = {FinanceMonthlyController.class, SpendingDefaultController.class})
public class FinanceExceptionHandler {

    @ExceptionHandler(FinanceException.class)
    public ResponseEntity<ApiResponse.Error> handle(FinanceException e) {
        return error(e.getStatus(), e.getCode(), e.getMessage());
    }

    /** ?income=abc 처럼 숫자 자리에 글자가 온 경우 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse.Error> typeMismatch(MethodArgumentTypeMismatchException e) {
        return badParam(e.getName());
    }

    /** ?income 자체를 빠뜨린 경우 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse.Error> missingParam(MissingServletRequestParameterException e) {
        return badParam(e.getParameterName());
    }

    /** 요청 본문이 JSON 이 아니거나, 금액 자리에 글자가 온 경우 (예: {"income":"abc"}) */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse.Error> unreadable(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 내용을 확인해 주세요.");
    }

    private static ResponseEntity<ApiResponse.Error> badParam(String name) {
        if ("income".equals(name)) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "금액을 확인해 주세요.");
        }
        if ("months".equals(name)) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_MONTHS", "조회 개월 수를 확인해 주세요.");
        }
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 값을 확인해 주세요.");
    }

    private static ResponseEntity<ApiResponse.Error> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(ApiResponse.Error.of(code, message, "req-" + UUID.randomUUID().toString().substring(0, 8)));
    }
}
