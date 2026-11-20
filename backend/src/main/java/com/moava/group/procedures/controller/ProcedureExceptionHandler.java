package com.moava.group.procedures.controller;

import com.moava.common.response.ApiResponse;
import com.moava.group.procedures.exception.ProcedureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestControllerAdvice(assignableTypes = {
        DissolutionController.class, PayoutOrderController.class, WithdrawalController.class
})
public class ProcedureExceptionHandler {

    @ExceptionHandler(ProcedureException.class)
    public ResponseEntity<ApiResponse.Error> handleProcedureException(ProcedureException exception) {
        return error(exception.getStatus(), exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse.Error> handleResponseStatusException(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return error(status, codeFor(status), exception.getReason());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse.Error> handleInvalidRequest(HttpMessageNotReadableException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 내용을 확인해 주세요.");
    }

    private static String codeFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "INVALID_REQUEST";
            case UNAUTHORIZED -> "UNAUTHORIZED";
            case FORBIDDEN -> "FORBIDDEN";
            case NOT_FOUND -> "NOT_FOUND";
            case CONFLICT -> "CONFLICT";
            default -> "PROCEDURE_ERROR";
        };
    }

    private static ResponseEntity<ApiResponse.Error> error(HttpStatus status, String code, String message) {
        String safeMessage = message != null ? message : "절차 요청을 처리하지 못했습니다.";
        String requestId = "req-" + UUID.randomUUID().toString().substring(0, 8);
        return ResponseEntity.status(status).body(ApiResponse.Error.of(code, safeMessage, requestId));
    }
}
