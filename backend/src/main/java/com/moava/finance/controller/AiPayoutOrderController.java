package com.moava.finance.controller;

import com.moava.common.response.ApiResponse;
import com.moava.finance.service.AiFinanceService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * AI 곗돈 수령 순서 추천 (MOA-34). 모임장만 쓸 수 있고, 회원 목록은 서버가 DB 에서 직접 읽는다.
 * POST /api/v1/groups/{groupId}/ai/payout-order   (본문 없음) → ML /risk/payout-order 응답 그대로
 * AI 는 제안만 한다. 실제 확정은 모임 절차(PayoutOrderController, 다른 팀원 담당)에서 한다.
 */
@RestController
@RequestMapping("/api/v1/groups/{groupId}/ai/payout-order")
public class AiPayoutOrderController {

    private final AiFinanceService service;

    public AiPayoutOrderController(AiFinanceService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> recommend(Authentication authentication, @PathVariable long groupId) {
        return ApiResponse.ok(service.payoutOrder(FinanceAuth.memberId(authentication), groupId),
                "req-" + UUID.randomUUID().toString().substring(0, 8));
    }
}
