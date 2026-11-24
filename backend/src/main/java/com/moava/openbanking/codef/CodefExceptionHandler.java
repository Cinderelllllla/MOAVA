package com.moava.openbanking.codef;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * CODEF 실계좌인증 컨트롤러 전용 예외 핸들러 (타 도메인 컨트롤러에 영향 없음)
 */
@Slf4j
@RestControllerAdvice(assignableTypes = CodefBankController.class)
public class CodefExceptionHandler {

    @ExceptionHandler(CodefException.class)
    public ResponseEntity<CodefDto.ErrorResponse> handleCodefException(CodefException e) {
        return build(e.getHttpStatus(), e.getCode(), e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CodefDto.ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return build(HttpStatus.BAD_REQUEST, "CODEF_INVALID_JSON", "요청 JSON 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CodefDto.ErrorResponse> handleUnexpected(Exception e) {
        log.error("[CODEF-ERROR] 실계좌인증 처리 중 예기치 않은 오류", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "CODEF_INTERNAL_ERROR", "실계좌인증 처리 중 오류가 발생했습니다.");
    }

    private static ResponseEntity<CodefDto.ErrorResponse> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .body(new CodefDto.ErrorResponse(code, message));
    }
}
