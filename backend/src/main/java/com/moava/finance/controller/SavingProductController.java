package com.moava.finance.controller;

import com.moava.common.response.ApiResponse;
import com.moava.finance.dto.SavingProductComparisonResponse;
import com.moava.finance.service.SavingProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * F14 계모임 시뮬레이터 — 시중 적금 금리 비교 API ({@code PRODUCTS_SAVING}).
 * {@code GET /api/v1/finance/saving-products?termMonths=12}
 */
@RestController
@RequestMapping("/api/v1/finance/saving-products")
public class SavingProductController {

    private final SavingProductService service;

    public SavingProductController(SavingProductService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<SavingProductComparisonResponse> compare(
            @RequestParam(required = false) Integer termMonths
    ) {
        return ApiResponse.ok(
                service.compare(termMonths),
                "req-" + UUID.randomUUID().toString().substring(0, 8)
        );
    }
}
