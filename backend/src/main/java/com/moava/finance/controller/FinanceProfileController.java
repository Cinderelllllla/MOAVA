package com.moava.finance.controller;

import com.moava.common.response.ApiResponse;
import com.moava.finance.dto.FinanceProfileResponse;
import com.moava.finance.dto.FinanceProfileUpsertRequest;
import com.moava.finance.dto.JourneyStepRequest;
import com.moava.finance.service.FinanceProfileService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * F05 금융 프로필 및 맞춤 추천 진행 단계 API.
 * - GET   /api/v1/finance/profile      : 저장된 금융 프로필 + 최신 위험 등급 기반 추천 결과 조회
 * - PUT   /api/v1/finance/profile      : 금융 프로필 저장/수정 (FINANCE_PROFILE Upsert)
 * - PATCH /api/v1/finance/profile/step : 맞춤 추천 4단계 진행 상태 저장
 */
@RestController
@RequestMapping("/api/v1/finance/profile")
public class FinanceProfileController {

    private final FinanceProfileService service;

    public FinanceProfileController(FinanceProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<FinanceProfileResponse> getProfile(Authentication authentication) {
        return ApiResponse.ok(service.getProfile(FinanceAuth.memberId(authentication)), requestId());
    }

    @PutMapping
    public ApiResponse<FinanceProfileResponse> saveProfile(Authentication authentication,
                                                           @RequestBody FinanceProfileUpsertRequest request) {
        return ApiResponse.ok(service.saveProfile(FinanceAuth.memberId(authentication), request), requestId());
    }

    @PatchMapping("/step")
    public ApiResponse<Map<String, Integer>> updateStep(Authentication authentication,
                                                        @RequestBody JourneyStepRequest request) {
        int step = service.updateJourneyStep(FinanceAuth.memberId(authentication), request == null ? null : request.step());
        return ApiResponse.ok(Map.of("journeyStep", step), requestId());
    }

    private static String requestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
