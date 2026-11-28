package com.moava.openbanking.codef;

import com.moava.openbanking.controller.MockBankAccountController;
import com.moava.openbanking.exception.KftcOpenBankingException;
import com.moava.openbanking.exception.KftcResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * CODEF 실계좌인증 및 프론트 연동용 모의 은행 컨트롤러 전용 예외 핸들러 (타 도메인 컨트롤러에 영향 없음)
 */
@Slf4j
@RestControllerAdvice(assignableTypes = {CodefBankController.class, MockBankAccountController.class})
public class CodefExceptionHandler {

    @ExceptionHandler(CodefException.class)
    public ResponseEntity<CodefDto.ErrorResponse> handleCodefException(CodefException e) {
        return build(e.getHttpStatus(), e.getCode(), e.getMessage());
    }

    @ExceptionHandler(KftcOpenBankingException.class)
    public ResponseEntity<CodefDto.ErrorResponse> handleKftcException(KftcOpenBankingException e) {
        KftcResponseCode rc = e.getResponseCode() != null
                ? e.getResponseCode()
                : KftcResponseCode.INTERNAL_ERROR;
        // 프론트 apiFetch가 401 응답 시 MOAVA 로그아웃을 수행하므로 내부 모의토큰 오류는 502로 변환
        HttpStatus status = rc.getHttpStatus() == HttpStatus.UNAUTHORIZED
                ? HttpStatus.BAD_GATEWAY
                : rc.getHttpStatus();
        String message = (e.getMessage() != null && !e.getMessage().isBlank())
                ? e.getMessage()
                : rc.getDefaultMessage();
        return build(status, rc.getCode(), message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CodefDto.ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return build(HttpStatus.BAD_REQUEST, "OPENBANKING_INVALID_JSON", "요청 JSON 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<CodefDto.ErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
        return build(HttpStatus.BAD_REQUEST, "OPENBANKING_MISSING_PARAM",
                "필수 요청 파라미터(" + e.getParameterName() + ")가 누락되었습니다.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<CodefDto.ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return build(HttpStatus.BAD_REQUEST, "OPENBANKING_INVALID_PARAM",
                "요청 파라미터(" + e.getName() + ") 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CodefDto.ErrorResponse> handleUnexpected(Exception e) {
        log.error("[OPENBANKING-ERROR] 오픈뱅킹/실계좌인증 처리 중 예기치 않은 오류", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "OPENBANKING_INTERNAL_ERROR", "오픈뱅킹 처리 중 오류가 발생했습니다.");
    }

    private static ResponseEntity<CodefDto.ErrorResponse> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .body(new CodefDto.ErrorResponse(code, message));
    }
}
