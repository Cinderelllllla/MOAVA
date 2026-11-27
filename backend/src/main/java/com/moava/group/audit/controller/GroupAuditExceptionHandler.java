package com.moava.group.audit.controller;

import com.moava.common.response.ApiResponse;
import com.moava.group.audit.service.GroupAuditException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;

/** F33 회원용 모임 운영 기록 컨트롤러 전용 예외 변환 → {error:{code,message}, requestId}. */
@RestControllerAdvice(assignableTypes = GroupAuditController.class)
public class GroupAuditExceptionHandler {

    @ExceptionHandler(GroupAuditException.class)
    public ResponseEntity<ApiResponse.Error> handle(GroupAuditException e) {
        return error(e.getStatus(), e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse.Error> badRequest(MethodArgumentTypeMismatchException e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 값을 확인해 주세요.");
    }

    private static ResponseEntity<ApiResponse.Error> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(ApiResponse.Error.of(code, message, "req-" + UUID.randomUUID().toString().substring(0, 8)));
    }
}
