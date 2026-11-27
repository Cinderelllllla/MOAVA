package com.moava.finance;

import com.moava.finance.controller.FinanceExceptionHandler;
import com.moava.finance.controller.FinanceMonthlyController;
import com.moava.finance.dto.MonthlyListResponse;
import com.moava.finance.service.FinanceMonthlyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 관리자 토큰(ROLE_ADMIN)으로는 회원 월별 기록 API 를 쓸 수 없어야 한다 (ADMIN_ID 와 MEMBER_ID 가 겹칠 수 있음) */
class FinanceMonthlyControllerAuthTest {

    FinanceMonthlyService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(FinanceMonthlyService.class);
        when(service.list(anyLong(), any())).thenReturn(new MonthlyListResponse(List.of()));
        mvc = MockMvcBuilders.standaloneSetup(new FinanceMonthlyController(service))
                .setControllerAdvice(new FinanceExceptionHandler())
                .build();
    }

    private static UsernamePasswordAuthenticationToken auth(String id, String role) {
        return new UsernamePasswordAuthenticationToken(id, null, List.of(new SimpleGrantedAuthority(role)));
    }

    @Test
    void 회원은_조회된다() throws Exception {
        mvc.perform(get("/api/v1/finance/monthly").principal(auth("7", "ROLE_MEMBER")))
                .andExpect(status().isOk());
        verify(service).list(7L, null);
    }

    @Test
    void 관리자_토큰은_일괄_저장도_403() throws Exception {
        mvc.perform(put("/api/v1/finance/monthly/batch").principal(auth("7", "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("{\"items\":[{\"yearMonth\":\"202611\",\"income\":1,\"expense\":1}]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("MEMBER_ONLY"));
        verifyNoInteractions(service);
    }

    @Test
    void 회원은_일괄_저장된다() throws Exception {
        when(service.upsertBatch(anyLong(), any())).thenReturn(new MonthlyListResponse(List.of(
                new MonthlyListResponse.Item("202611", 1L, 1L, 0L))));
        mvc.perform(put("/api/v1/finance/monthly/batch").principal(auth("7", "ROLE_MEMBER"))
                        .contentType("application/json")
                        .content("{\"items\":[{\"yearMonth\":\"202611\",\"income\":1,\"expense\":1}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].yearMonth").value("202611"));
        verify(service).upsertBatch(eq(7L), any());
    }

    @Test
    void 관리자_토큰은_403() throws Exception {
        mvc.perform(get("/api/v1/finance/monthly").principal(auth("7", "ROLE_ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("MEMBER_ONLY"));
        verifyNoInteractions(service);
    }
}
