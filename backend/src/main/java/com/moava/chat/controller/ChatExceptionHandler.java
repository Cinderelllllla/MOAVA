package com.moava.chat.controller;

import com.moava.chat.service.ChatException;
import com.moava.common.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;

/** 채팅 REST 컨트롤러(회원용·관리자 열람용) 전용 오류 변환. 다른 패키지에는 영향을 주지 않는다. */
@RestControllerAdvice(assignableTypes = {ChatRestController.class, AdminChatViewController.class})
public class ChatExceptionHandler {

    @ExceptionHandler(ChatException.class)
    public ResponseEntity<ApiResponse.Error> handle(ChatException e) {
        return error(e.getStatus(), e.getCode(), e.getMessage());
    }

    /** /groups/abc/messages, ?beforeId=abc 처럼 숫자 자리에 글자가 온 경우 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse.Error> typeMismatch(MethodArgumentTypeMismatchException e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 값을 확인해 주세요.");
    }

    private static ResponseEntity<ApiResponse.Error> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(ApiResponse.Error.of(code, message, "req-" + UUID.randomUUID().toString().substring(0, 8)));
    }
}
