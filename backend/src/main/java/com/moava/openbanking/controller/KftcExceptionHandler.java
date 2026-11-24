package com.moava.openbanking.controller;

import com.moava.openbanking.dto.KftcBaseResponse;
import com.moava.openbanking.exception.KftcOpenBankingException;
import com.moava.openbanking.exception.KftcResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 모의 KFTC 오픈뱅킹 전용 예외 변환기 (타 도메인 컨트롤러에 영향 없음)
 */
@Slf4j
@RestControllerAdvice(assignableTypes = {KftcOAuthController.class, KftcOpenBankingController.class})
public class KftcExceptionHandler {

    @ExceptionHandler(KftcOpenBankingException.class)
    public ResponseEntity<KftcBaseResponse> handleKftcException(KftcOpenBankingException e) {
        return buildError(e.getResponseCode(), e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<KftcBaseResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return buildError(KftcResponseCode.INVALID_PARAMETER, "요청 본문(JSON) 또는 숫자 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<KftcBaseResponse> handleMissingParam(MissingServletRequestParameterException e) {
        return buildError(KftcResponseCode.INVALID_PARAMETER, "필수 파라미터(" + e.getParameterName() + ")가 누락되었습니다.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<KftcBaseResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return buildError(KftcResponseCode.INVALID_PARAMETER, "파라미터(" + e.getName() + ") 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<KftcBaseResponse> handleUnexpected(Exception e) {
        log.error("[KFTC-ERROR] 오픈뱅킹 요청 처리 중 예기치 않은 오류", e);
        return buildError(KftcResponseCode.INTERNAL_ERROR, KftcResponseCode.INTERNAL_ERROR.getDefaultMessage());
    }

    private static ResponseEntity<KftcBaseResponse> buildError(KftcResponseCode code, String message) {
        return ResponseEntity.status(code.getHttpStatus())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .body(KftcBaseResponse.errorOf(code, message));
    }
}
