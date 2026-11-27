package com.moava.finance.service;

import com.moava.finance.dto.FinanceProfileResponse;
import com.moava.finance.dto.FinanceProfileRow;
import com.moava.finance.dto.FinanceProfileUpsertRequest;
import com.moava.finance.dto.RiskScoreRow;
import com.moava.finance.mapper.AiPayoutMapper;
import com.moava.finance.mapper.FinanceProfileMapper;
import com.moava.finance.dto.AiContributionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FinanceProfileServiceTest {

    private FinanceProfileMapper mapper;
    private FinanceProfileService service;

    @BeforeEach
    void setUp() {
        mapper = mock(FinanceProfileMapper.class);
        service = new FinanceProfileService(mapper);
    }

    @Test
    void getProfile_whenNotSaved_returnsEmpty() {
        when(mapper.selectProfile(7L)).thenReturn(null);

        FinanceProfileResponse res = service.getProfile(7L);

        assertFalse(res.exists());
        assertEquals(1, res.journeyStep());
        assertNull(res.assessment());
        verify(mapper, never()).selectLatestRiskScore(anyLong());
    }

    @Test
    void getProfile_withProfileAndLatestRiskScore_restoresAssessmentWithoutProb() {
        when(mapper.selectProfile(7L)).thenReturn(new FinanceProfileRow(
                3_200_000L, 1_200_000L, 880_000L, 350_000L, 250_000L, 200_000L,
                "Y", "2026-11-26 19:00", 3, "2026-11-26 19:05"
        ));
        when(mapper.selectLatestRiskScore(7L)).thenReturn(new RiskScoreRow(
                "STABLE", new BigDecimal("0.50"), "N", "2026-11-26 19:01"
        ));

        FinanceProfileResponse res = service.getProfile(7L);

        assertTrue(res.exists());
        assertEquals(3_200_000L, res.income());
        assertEquals(3, res.journeyStep());
        assertNotNull(res.assessment());
        // available = 3,200,000 - 1,200,000 - 880,000 - 350,000 - 250,000 = 520,000
        // byRisk = 520,000 * 0.50 = 260,000 <= cap (3,200,000 * 0.12 = 384,000) -> floor(260,000 / 50,000) * 50,000 = 250,000
        assertEquals(520_000L, res.assessment().availableAmount());
        assertEquals(250_000L, res.assessment().recommendedAmount());
        assertFalse(res.assessment().capped());
        assertEquals("STABLE", res.assessment().riskLevel());
        assertEquals("안정", res.assessment().riskLabel());
        assertEquals(0.5, res.assessment().safetyFactor(), 0.001);
        assertEquals("2026-11-26 19:01", res.assessment().scoredAt());
    }

    @Test
    void getProfile_appliesIncomeCap12Percent() {
        when(mapper.selectProfile(7L)).thenReturn(new FinanceProfileRow(
                3_000_000L, 500_000L, 500_000L, 0L, 0L, 400_000L,
                "Y", "2026-11-26 19:00", 2, "2026-11-26 19:00"
        ));
        when(mapper.selectLatestRiskScore(7L)).thenReturn(new RiskScoreRow(
                "STABLE", new BigDecimal("0.50"), "N", "2026-11-26 19:01"
        ));

        FinanceProfileResponse res = service.getProfile(7L);

        // available = 2,000,000; byRisk = 1,000,000 > cap (360,000) -> capped=true, rec=350,000
        assertTrue(res.assessment().capped());
        assertEquals(350_000L, res.assessment().recommendedAmount());
    }

    @Test
    void saveProfile_requiresConsentAndNonNegativeAmounts() {
        FinanceException noConsent = assertThrows(FinanceException.class, () ->
                service.saveProfile(7L, new FinanceProfileUpsertRequest(
                        3_000_000L, 1_000_000L, 500_000L, 200_000L, 100_000L, 200_000L, false, 2
                )));
        assertEquals(HttpStatus.BAD_REQUEST, noConsent.getStatus());
        assertEquals("CONSENT_REQUIRED", noConsent.getCode());

        FinanceException negAmount = assertThrows(FinanceException.class, () ->
                service.saveProfile(7L, new FinanceProfileUpsertRequest(
                        -1L, 0L, 0L, 0L, 0L, 0L, true, 2
                )));
        assertEquals("INVALID_AMOUNT", negAmount.getCode());
    }

    @Test
    void saveProfile_upsertsAndReturnsReloadedProfile() {
        when(mapper.selectProfile(7L)).thenReturn(new FinanceProfileRow(
                3_200_000L, 1_200_000L, 880_000L, 350_000L, 250_000L, 200_000L,
                "Y", "2026-11-26 19:00", 2, "2026-11-26 19:00"
        ));
        when(mapper.selectLatestRiskScore(7L)).thenReturn(null);

        FinanceProfileResponse res = service.saveProfile(7L, new FinanceProfileUpsertRequest(
                3_200_000L, 1_200_000L, 880_000L, 350_000L, 250_000L, 200_000L, true, null
        ));

        verify(mapper).upsertProfile(7L, 3_200_000L, 1_200_000L, 880_000L, 350_000L, 250_000L, 200_000L, "Y", 2);
        assertTrue(res.exists());
        assertEquals(2, res.journeyStep());
    }

    @Test
    void aiContribution_recordsRiskScoreToDbAndStripsProbFromResponse() {
        MlClient ml = mock(MlClient.class);
        AiPayoutMapper payoutMapper = mock(AiPayoutMapper.class);
        when(ml.contribution(anyMap())).thenReturn(Map.of(
                "available_amount", 520_000,
                "recommended_amount", 250_000,
                "capped", false,
                "risk", Map.of(
                        "default_probability", 0.08421,
                        "risk_level", "STABLE",
                        "risk_label", "안정",
                        "safety_factor", 0.5,
                        "source", "persona"
                )
        ));

        AiFinanceService aiService = new AiFinanceService(ml, payoutMapper, service);
        Map<String, Object> result = aiService.contribution(7L,
                new AiContributionRequest(3_200_000L, 2_080_000L, 350_000L, 250_000L));

        verify(mapper).insertRiskScore(
                eq(7L),
                eq(new BigDecimal("0.0842")),
                eq("STABLE"),
                eq(new BigDecimal("0.50")),
                eq("N")
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> risk = (Map<String, Object>) result.get("risk");
        assertEquals("STABLE", risk.get("risk_level"));
        assertFalse(risk.containsKey("default_probability"));
    }
}
