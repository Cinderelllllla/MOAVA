package com.moava.finance.controller;

import com.moava.common.response.ApiResponse;
import com.moava.finance.dto.SpendingDefaultResponse;
import com.moava.finance.service.SpendingDefaultService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * F07 지출 미입력 기본값 제안 (S03 ①).
 * GET /api/v1/finance/spending-defaults?income=3200000  (월수입, 원)
 * KOSIS 데이터가 없으면 available=false. 사용자가 입력한 값이 항상 우선이라 화면은 placeholder 로만 쓴다.
 */
@RestController
@RequestMapping("/api/v1/finance/spending-defaults")
public class SpendingDefaultController {

    private final SpendingDefaultService service;

    public SpendingDefaultController(SpendingDefaultService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<SpendingDefaultResponse> suggest(@RequestParam Long income) {
        return ApiResponse.ok(service.suggest(income), "req-" + UUID.randomUUID().toString().substring(0, 8));
    }
}
