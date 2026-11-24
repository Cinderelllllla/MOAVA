package com.moava.proofs.controller;

import com.moava.common.response.ApiResponse;
import com.moava.proofs.dto.PaymentStatusResponse;
import com.moava.proofs.service.ProofQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
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

    private static String requestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
}