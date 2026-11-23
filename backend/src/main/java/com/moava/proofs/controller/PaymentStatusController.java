package com.moava.proofs.controller;

import com.moava.common.response.ApiResponse;
import com.moava.proofs.dto.PaymentStatusResponse;
import com.moava.proofs.service.ProofQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * F25 계원별 납입 현황 API (기능명세서: GET /groups/{id}/payments)
 * - 증빙 컨트롤러(/payment-proofs)와 기본 주소가 달라서 클래스를 분리
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/groups/{groupId}/payments")
public class PaymentStatusController {

    private final ProofQueryService proofQueryService;

    /** F25 납입 현황 — ?payMonth=YYYYMM (생략 시 이번 달) */
    @GetMapping
    public ApiResponse<PaymentStatusResponse> getPaymentStatus(
            @PathVariable Long groupId,
            @RequestParam(required = false) String payMonth,
            Authentication authentication) {
        Long memberId = loginMemberId(authentication);
        return ApiResponse.ok(proofQueryService.getPaymentStatus(groupId, memberId, payMonth), requestId());
    }

    // ---------- 로그인 회원 (PaymentProofController와 같음) ----------

    private static Long loginMemberId(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인 정보가 올바르지 않습니다.");
        }
    }

    // ---------- 오류 응답 (이 컨트롤러에서 난 예외만 처리) ----------

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

    /** groupId=abc처럼 경로 값이 숫자가 아닌 경우 → 400 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse.Error> handleBadRequest(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.Error.of("INVALID_REQUEST", "요청 형식이 올바르지 않습니다.", requestId()));
    }

    private static String requestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
}