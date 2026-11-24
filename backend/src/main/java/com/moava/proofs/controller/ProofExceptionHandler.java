package com.moava.proofs.controller;

import com.moava.common.response.ApiResponse;
import com.moava.overdue.controller.OverdueController;
import com.moava.references.controller.RefNoController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * 납입 증빙(MOA-45: F22·F23·F24·F25) API 공통 오류 응답 — API 명세서 v0.5 §2 형식
 * 팀 컨벤션(GroupCreateExceptionHandler 등)처럼 assignableTypes로 대상 컨트롤러만 지정 → 다른 기능에는 영향 없음
 */
@RestControllerAdvice(assignableTypes = {
        PaymentProofController.class,     // F22·F23 증빙 등록·조회·심사
        PaymentStatusController.class,    // F25 납입 현황
        RefNoController.class,             // F24 거래 참조번호
        OverdueController.class           // F26 미납 관리
})
public class ProofExceptionHandler {

    /** Service·Controller의 ResponseStatusException → 명세 §2 오류 형식 */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse.Error> handle(ResponseStatusException e) {
        String code = switch (e.getStatusCode().value()) {
            case 400 -> "INVALID_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 409 -> "INVALID_STATE";
            default  -> "INTERNAL_ERROR";
        };
        String message = e.getReason() != null ? e.getReason() : "요청을 처리할 수 없습니다.";
        return ResponseEntity.status(e.getStatusCode())
                .body(ApiResponse.Error.of(code, message, requestId()));
    }

    /** 요청 값을 파라미터로 바꾸다 실패한 경우 (Controller 실행 전) → 400 */
    @ExceptionHandler({
            MissingServletRequestPartException.class,   // 증빙 files 파트 없음
            BindException.class,                        // 증빙 폼 값 형식 오류 (payDate, amount, roundNo 등)
            MethodArgumentTypeMismatchException.class,  // 경로 값이 숫자가 아님 (groupId=abc 등)
            HttpMessageNotReadableException.class       // JSON 형식 오류 (심사·참조번호 요청)
    })
    public ResponseEntity<ApiResponse.Error> handleBadRequest(Exception e) {
        String message;
        if (e instanceof MissingServletRequestPartException) {
            message = "증빙 파일을 첨부해 주세요.";
        } else if (e instanceof BindException) {
            message = "입력값 형식이 올바르지 않습니다. (납입일 YYYY-MM-DD, 금액·회차는 숫자)";
        } else {
            message = "요청 형식이 올바르지 않습니다.";
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.Error.of("INVALID_REQUEST", message, requestId()));
    }

    private static String requestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
}