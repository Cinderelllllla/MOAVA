package com.moava.group.create.controller;

import com.moava.group.account.controller.GroupAccountController;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = {
        GroupCreateController.class,
        GroupJoinCodeController.class,
        GroupInviteLinkController.class,
        GroupAccountController.class
})
public class GroupCreateExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(
            IllegalArgumentException e
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleInvalidJson(
            HttpMessageNotReadableException e
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "message",
                        "요청 데이터 형식이 올바르지 않습니다."
                ));
    }

    // F19 - 경로 변수 및 요청 파라미터 타입 오류 처리
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(
            MethodArgumentTypeMismatchException e
    ) {

        String message = "요청 파라미터 형식이 올바르지 않습니다.";

        if ("groupId".equals(e.getName())) {
            message = "유효하지 않은 모임 ID입니다.";
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", message));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleSecurityException(
            SecurityException e
    ) {
        HttpStatus status = "로그인이 필요합니다.".equals(e.getMessage())
                ? HttpStatus.UNAUTHORIZED
                : HttpStatus.FORBIDDEN;

        return ResponseEntity
                .status(status)
                .body(Map.of("message", e.getMessage()));
    }

    // F19 - HTTP 상태 코드가 지정된 예외 처리
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(
            ResponseStatusException e
    ) {

        String message = e.getReason();

        if (message == null || message.isBlank()) {
            message = "요청을 처리할 수 없습니다.";
        }

        return ResponseEntity
                .status(e.getStatusCode())
                .body(Map.of("message", message));
    }

    // 모임 상태 및 인증코드 관련 예외 처리
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(
            IllegalStateException e
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "message",
                        e.getMessage() != null
                                ? e.getMessage()
                                : "현재 상태에서는 요청을 처리할 수 없습니다."
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpectedException(
            Exception e
    ) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "message",
                        "서버 오류가 발생했습니다."
                ));
    }
}
