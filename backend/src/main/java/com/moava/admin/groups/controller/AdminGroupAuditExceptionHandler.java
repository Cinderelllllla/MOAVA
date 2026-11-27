package com.moava.admin.groups.controller;

import com.moava.common.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * F38 (A02) 관리자 계모임 관리·이력 감사 API 전용 예외 처리기.
 */
@RestControllerAdvice(assignableTypes = {AdminGroupAuditController.class})
public class AdminGroupAuditExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse.Error> handleResponseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return error(status, codeFor(status), exception.getReason());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse.Error> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 파라미터 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse.Error> handleIllegalArgument(IllegalArgumentException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse.Error> handleIllegalState(IllegalStateException exception) {
        return error(HttpStatus.CONFLICT, "CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse.Error> handleUnreadable(HttpMessageNotReadableException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 내용을 확인해 주세요.");
    }

    private static String codeFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "INVALID_REQUEST";
            case UNAUTHORIZED -> "UNAUTHORIZED";
            case FORBIDDEN -> "FORBIDDEN";
            case NOT_FOUND -> "NOT_FOUND";
            case CONFLICT -> "CONFLICT";
            default -> "ADMIN_GROUP_AUDIT_ERROR";
        };
    }

    private static ResponseEntity<ApiResponse.Error> error(HttpStatus status, String code, String message) {
        String safeMessage = (message == null || message.isBlank())
                ? "계모임 관리 및 감사 이력 요청을 처리하지 못했습니다."
                : message;
        String requestId = "req-" + UUID.randomUUID().toString().substring(0, 8);
        return ResponseEntity.status(status).body(ApiResponse.Error.of(code, safeMessage, requestId));
    }
}
