package com.moava.finance.controller;

import com.moava.finance.dto.SavingProductComparisonResponse;
import com.moava.finance.service.FinanceException;
import com.moava.finance.service.SavingProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F14 GET /api/v1/finance/saving-products 컨트롤러 단위 테스트 */
@ExtendWith(MockitoExtension.class)
class SavingProductControllerTest {

    @Mock
    SavingProductService service;

    @InjectMocks
    SavingProductController controller;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new FinanceExceptionHandler())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/finance/saving-products: 적금 금리 비교 요약을 ApiResponse 로 반환한다")
    void compare_returns200() throws Exception {
        when(service.compare(12)).thenReturn(new SavingProductComparisonResponse(
                true,
                12,
                12,
                2,
                new BigDecimal("3.10"),
                new BigDecimal("4.10"),
                new BigDecimal("4.50"),
                "2026-11-26",
                List.of(new SavingProductComparisonResponse.Item(
                        "토스뱅크",
                        "굴비적금",
                        12,
                        new BigDecimal("3.00"),
                        new BigDecimal("4.50")
                ))
        ));

        mockMvc.perform(get("/api/v1/finance/saving-products").param("termMonths", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.matchedTermMonths").value(12))
                .andExpect(jsonPath("$.data.topMaxRate").value(4.5))
                .andExpect(jsonPath("$.data.topProducts[0].bankName").value("토스뱅크"));
    }

    @Test
    @DisplayName("GET /api/v1/finance/saving-products: 잘못된 기간이면 400 에러 응답을 반환한다")
    void compare_invalidTerm_returns400() throws Exception {
        when(service.compare(0)).thenThrow(
                new FinanceException(HttpStatus.BAD_REQUEST, "INVALID_TERM", "저축 기간은 1~120개월 사이여야 합니다.")
        );

        mockMvc.perform(get("/api/v1/finance/saving-products").param("termMonths", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TERM"));
    }
}
