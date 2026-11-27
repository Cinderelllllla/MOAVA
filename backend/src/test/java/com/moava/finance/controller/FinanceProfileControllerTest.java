package com.moava.finance.controller;

import com.moava.finance.dto.FinanceProfileResponse;
import com.moava.finance.service.FinanceException;
import com.moava.finance.service.FinanceProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FinanceProfileControllerTest {

    private FinanceProfileService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(FinanceProfileService.class);
        mvc = MockMvcBuilders.standaloneSetup(new FinanceProfileController(service))
                .setControllerAdvice(new FinanceExceptionHandler())
                .build();
    }

    private static UsernamePasswordAuthenticationToken member(long id) {
        return new UsernamePasswordAuthenticationToken(
                String.valueOf(id), null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
    }

    @Test
    void getProfile_returnsSavedProfileAndAssessment() throws Exception {
        when(service.getProfile(7L)).thenReturn(new FinanceProfileResponse(
                true, 3_200_000L, 1_200_000L, 880_000L, 350_000L, 250_000L, 200_000L,
                true, "2026-11-26 19:00", 3, "2026-11-26 19:05",
                new FinanceProfileResponse.SavedAssessment(520_000L, 250_000L, false, "STABLE", "안정", 0.5, "2026-11-26 19:01")
        ));

        mvc.perform(get("/api/v1/finance/profile").principal(member(7)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(true))
                .andExpect(jsonPath("$.data.income").value(3200000))
                .andExpect(jsonPath("$.data.journeyStep").value(3))
                .andExpect(jsonPath("$.data.assessment.riskLabel").value("안정"))
                .andExpect(jsonPath("$.data.assessment.recommendedAmount").value(250000));
    }

    @Test
    void saveProfile_persistsAndReturnsProfile() throws Exception {
        when(service.saveProfile(eq(7L), any())).thenReturn(new FinanceProfileResponse(
                true, 3_200_000L, 1_200_000L, 880_000L, 350_000L, 250_000L, 200_000L,
                true, "2026-11-26 19:00", 2, "2026-11-26 19:00", null
        ));

        mvc.perform(put("/api/v1/finance/profile").principal(member(7))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"income":3200000,"fixedExp":1200000,"variableExp":880000,"saving":350000,"reserve":250000,"goalAmount":200000,"consent":true,"journeyStep":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(true))
                .andExpect(jsonPath("$.data.journeyStep").value(2));
    }

    @Test
    void updateStep_invalidStepReturns400() throws Exception {
        when(service.updateJourneyStep(7L, 9))
                .thenThrow(new FinanceException(HttpStatus.BAD_REQUEST, "INVALID_STEP", "진행 단계는 1~4 사이여야 합니다."));

        mvc.perform(patch("/api/v1/finance/profile/step").principal(member(7))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"step\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_STEP"));
    }

    @Test
    void adminToken_returns403() throws Exception {
        var admin = new UsernamePasswordAuthenticationToken(
                "1", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        mvc.perform(get("/api/v1/finance/profile").principal(admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("MEMBER_ONLY"));
    }
}
