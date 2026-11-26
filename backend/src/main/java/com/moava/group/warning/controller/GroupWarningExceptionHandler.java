package com.moava.group.warning.controller;

import com.moava.common.response.ApiResponse;
import com.moava.group.warning.service.GroupWarningException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;

/** 경고·강제 탈퇴 컨트롤러 전용 오류 변환 → {error:{code,message}, requestId}. 다른 패키지에는 영향을 주지 않는다. */
@RestControllerAdvice(assignableTypes = GroupWarningController.class)
public class GroupWarningExceptionHandler {

    @ExceptionHandler(GroupWarningException.class)
    public ResponseEntity<ApiResponse.Error> handle(GroupWarningException e) {
        return error(e.getStatus(), e.getCode(), e.getMessage());
    }

    /** /groups/abc/members/1 처럼 숫자 자리에 글자가 오거나, 본문이 JSON 이 아닌 경우 */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse.Error> badRequest(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 값을 확인해 주세요.");
    }

    private static ResponseEntity<ApiResponse.Error> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(ApiResponse.Error.of(code, message, "req-" + UUID.randomUUID().toString().substring(0, 8)));
    }
}