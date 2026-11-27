package com.moava.group.payout.controller;

import com.moava.common.response.ApiResponse;
import com.moava.group.payout.dto.PayoutReceiptActionResponse;
import com.moava.group.payout.dto.PayoutReceiptSummaryResponse;
import com.moava.group.payout.service.PayoutReceiptException;
import com.moava.group.payout.service.PayoutReceiptService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * F28 곗돈 수령 완료 표시 API.
 * <pre>
 * GET  /api/v1/groups/{groupId}/payout-receipts                     모임 수령 완료 현황 조회 (ACTIVE 회원·모임장)
 * POST /api/v1/groups/{groupId}/members/{memberId}/payout-received  곗돈 수령 완료 표시 (모임장 전용, 순서 확정 후)
 * </pre>
 * SecurityConfig 가 /api/v1/groups/** 를 ROLE_MEMBER 로 보호하며, 컨트롤러에서도 회원 토큰을 재확인한다.
 */
@RestController
@RequestMapping("/api/v1/groups/{groupId}")
public class PayoutReceiptController {

    private final PayoutReceiptService service;

    public PayoutReceiptController(PayoutReceiptService service) {
        this.service = service;
    }

    @GetMapping("/payout-receipts")
    public ApiResponse<PayoutReceiptSummaryResponse> getSummary(
            Authentication authentication,
            @PathVariable long groupId
    ) {
        return ApiResponse.ok(service.getSummary(requireMemberId(authentication), groupId), requestId());
    }

    @PostMapping("/members/{memberId}/payout-received")
    public ApiResponse<PayoutReceiptActionResponse> markReceived(
            Authentication authentication,
            @PathVariable long groupId,
            @PathVariable long memberId
    ) {
        return ApiResponse.ok(
                service.markReceived(requireMemberId(authentication), groupId, memberId),
                requestId()
        );
    }

    static long requireMemberId(Authentication authentication) {
        boolean isMember = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_MEMBER".equals(a.getAuthority()));
        if (!isMember) {
            throw new PayoutReceiptException(HttpStatus.FORBIDDEN, "MEMBER_ONLY", "회원만 이용할 수 있어요.");
        }
        try {
            long id = Long.parseLong(authentication.getName());
            if (id <= 0) {
                throw new PayoutReceiptException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "로그인 정보가 올바르지 않아요.");
            }
            return id;
        } catch (NumberFormatException e) {
            throw new PayoutReceiptException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "로그인 정보가 올바르지 않아요.");
        }
    }

    private static String requestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
