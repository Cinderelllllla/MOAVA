package com.moava.finance;

import com.moava.finance.controller.FinanceExceptionHandler;
import com.moava.finance.controller.FinanceMonthlyController;
import com.moava.finance.controller.SpendingDefaultController;
import com.moava.finance.service.FinanceMonthlyService;
import com.moava.finance.service.SpendingDefaultService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 잘못된 요청도 Spring 기본 "Bad Request" 대신 {error:{code,message}} 한국어 형식으로 나오는지 (DB 불필요) */
class FinanceExceptionHandlerTest {

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new SpendingDefaultController(mock(SpendingDefaultService.class)),
                        new FinanceMonthlyController(mock(FinanceMonthlyService.class)))
                .setControllerAdvice(new FinanceExceptionHandler())
                .build();
    }

    @Test
    void 월수입에_글자를_넣으면_금액_확인_안내() throws Exception {
        mvc.perform(get("/api/v1/finance/spending-defaults").param("income", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_AMOUNT"))
                .andExpect(jsonPath("$.error.message").value("금액을 확인해 주세요."));
    }

    @Test
    void 월수입을_빠뜨리면_금액_확인_안내() throws Exception {
        mvc.perform(get("/api/v1/finance/spending-defaults"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_AMOUNT"));
    }

    @Test
    void 저장_본문_금액이_글자면_요청_확인_안내() throws Exception {
        mvc.perform(put("/api/v1/finance/monthly")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"yearMonth\":\"202611\",\"income\":\"abc\",\"expense\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("요청 내용을 확인해 주세요."));
    }
}
