package com.moava.finance.controller;

import com.moava.common.response.ApiResponse;
import com.moava.finance.dto.AiContributionRequest;
import com.moava.finance.service.AiFinanceService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * AI 금융 분석 (MOA-34). 브라우저 → Spring(로그인 확인) → ML 서버.
 * <pre>
 * POST /api/v1/finance/ai/contribution   {"monthly_income":3200000,"monthly_expense":2080000,"monthly_saving":350000,"emergency_fund":250000}
 *                                        → ML /risk/contribution 응답 그대로 (회원 번호는 토큰에서)
 * GET  /api/v1/finance/ai/policy         → {round_unit, income_cap_ratio, risk_levels}
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/finance/ai")
public class AiFinanceController {

    private final AiFinanceService service;

    public AiFinanceController(AiFinanceService service) {
        this.service = service;
    }

    @PostMapping("/contribution")
    public ApiResponse<Map<String, Object>> contribution(Authentication authentication,
                                                         @RequestBody AiContributionRequest request) {
        return ApiResponse.ok(service.contribution(FinanceAuth.memberId(authentication), request), requestId());
    }

    /** 개인 정보가 아닌 정책 값이라 로그인만 되어 있으면 된다 (/api/** 는 SecurityConfig 가 로그인 필요로 막음) */
    @GetMapping("/policy")
    public ApiResponse<Map<String, Object>> policy() {
        return ApiResponse.ok(service.policy(), requestId());
    }

    private static String requestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
