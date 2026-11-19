package com.moava.finance.controller;

import com.moava.common.response.ApiResponse;
import com.moava.finance.dto.MonthlyListResponse;
import com.moava.finance.dto.MonthlyUpsertRequest;
import com.moava.finance.service.FinanceMonthlyService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * F10 월별 수입·지출 기록 (S05, S04 차트). 금액은 원 단위.
 *
 * <pre>
 * GET    /api/v1/finance/monthly?months=6       최근 N개월 (월순)
 * PUT    /api/v1/finance/monthly                {"yearMonth":"202611","income":3200000,"expense":2080000} 저장(덮어쓰기)
 * DELETE /api/v1/finance/monthly/{yearMonth}    삭제
 * </pre>
 * 로그인(JWT)한 본인의 기록만 다룬다.
 */
@RestController
@RequestMapping("/api/v1/finance/monthly")
public class FinanceMonthlyController {

    private final FinanceMonthlyService service;

    public FinanceMonthlyController(FinanceMonthlyService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<MonthlyListResponse> list(Authentication authentication,
                                                 @RequestParam(required = false) Integer months) {
        return ApiResponse.ok(service.list(memberId(authentication), months), requestId());
    }

    @PutMapping
    public ApiResponse<MonthlyListResponse.Item> upsert(Authentication authentication,
                                                        @RequestBody MonthlyUpsertRequest request) {
        return ApiResponse.ok(service.upsert(memberId(authentication), request), requestId());
    }

    @DeleteMapping("/{yearMonth}")
    public ApiResponse<Map<String, Object>> delete(Authentication authentication, @PathVariable String yearMonth) {
        service.delete(memberId(authentication), yearMonth);
        return ApiResponse.ok(Map.of("yearMonth", yearMonth, "deleted", true), requestId());
    }

    private static long memberId(Authentication authentication) {
        return Long.parseLong(authentication.getName()); // JwtAuthenticationFilter 가 subject(MEMBER_ID)를 이름으로 넣어 준다
    }

    private static String requestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
