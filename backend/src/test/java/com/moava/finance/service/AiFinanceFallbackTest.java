package com.moava.finance.service;

import com.moava.finance.dto.AiContributionRequest;
import com.moava.finance.dto.RiskScoreRow;
import com.moava.finance.dto.SelfReportRequest;
import com.moava.finance.mapper.AiPayoutMapper;
import com.moava.finance.mapper.FinanceProfileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** MOA-95 F09 ML 장애 시 대체값 (ML 서버·DB 불필요, Mockito) */
class AiFinanceFallbackTest {

    private static final SelfReportRequest ANSWERS = new SelfReportRequest("0", "NONE", "2", "UNDER_30");

    MlClient ml;
    FinanceProfileMapper mapper;
    AiFinanceService service;

    @BeforeEach
    void setUp() {
        ml = mock(MlClient.class);
        mapper = mock(FinanceProfileMapper.class);
        service = new AiFinanceService(ml, mock(AiPayoutMapper.class), new FinanceProfileService(mapper));
    }

    private void mlDown() {
        when(ml.contribution(anyMap())).thenThrow(new FinanceException(HttpStatus.SERVICE_UNAVAILABLE,
                "ML_UNAVAILABLE", "분석 서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요."));
    }

    /** 월수입 200만, 지출 30만, 저축 30만, 비상금 30만 → 가용액 110만 */
    private static AiContributionRequest req() {
        return new AiContributionRequest(2_000_000L, 300_000L, 300_000L, 300_000L, ANSWERS);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> risk(Map<String, Object> res) {
        return (Map<String, Object>) res.get("risk");
    }

    @Test
    void ML_꺼짐_직전_기록이_있으면_그_등급으로_계산하고_FALLBACK_Y_로_복사_저장() {
        mlDown();
        when(mapper.selectLatestRiskScore(7L)).thenReturn(new RiskScoreRow("RISK", new BigDecimal("0.20"), "N", "2026-11-27 10:00"));

        Map<String, Object> res = service.contribution(7L, req());

        assertEquals(true, res.get("fallback"));
        assertEquals("PREVIOUS", res.get("fallback_source"));
        assertEquals("RISK", risk(res).get("risk_level"));
        assertEquals("위험", risk(res).get("risk_label"));
        assertEquals(0.2, risk(res).get("safety_factor"));
        assertEquals(1_100_000L, res.get("available_amount"));
        assertEquals(200_000L, res.get("recommended_amount"));   // 110만 × 0.2 = 22만 → 20만
        assertEquals(false, res.get("capped"));
        verify(mapper).insertRiskScoreCopyOfLatest(7L);
        verify(mapper, never()).insertRiskScore(anyLong(), any(), any(), any(), any());
    }

    @Test
    void ML_꺼짐_기록이_없으면_CAUTION_035_이고_저장하지_않는다() {
        mlDown();
        when(mapper.selectLatestRiskScore(7L)).thenReturn(null);

        Map<String, Object> res = service.contribution(7L, req());

        assertEquals(true, res.get("fallback"));
        assertEquals("DEFAULT", res.get("fallback_source"));
        assertEquals("CAUTION", risk(res).get("risk_level"));
        assertEquals("주의", risk(res).get("risk_label"));
        assertEquals(0.35, risk(res).get("safety_factor"));
        // 110만 × 0.35 = 38.5만 > 월수입 12% 24만 → 상한 24만 → 20만
        assertEquals(200_000L, res.get("recommended_amount"));
        assertEquals(true, res.get("capped"));
        assertEquals(50_000L, res.get("round_unit"));
        assertEquals(0.12, res.get("income_cap_ratio"));
        verify(mapper, never()).insertRiskScoreCopyOfLatest(anyLong());
        verify(mapper, never()).insertRiskScore(anyLong(), any(), any(), any(), any());
    }

    @Test
    void ML_꺼짐이어도_자가_응답이_없으면_대체값_없이_400() {
        FinanceException e = assertThrows(FinanceException.class, () -> service.contribution(7L,
                new AiContributionRequest(2_000_000L, 300_000L, 300_000L, 300_000L, null)));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertEquals("INVALID_SELF_REPORT", e.getCode());
        verifyNoInteractions(ml, mapper);
    }

    @Test
    void ML_입력_오류는_대체값_없이_그대로_400() {
        when(ml.contribution(anyMap())).thenThrow(new FinanceException(HttpStatus.BAD_REQUEST, "ML_BAD_REQUEST", "입력값을 확인해 주세요."));

        FinanceException e = assertThrows(FinanceException.class, () -> service.contribution(7L, req()));
        assertEquals("ML_BAD_REQUEST", e.getCode());
        verify(mapper, never()).selectLatestRiskScore(anyLong());
    }

    @Test
    void ML_정상이면_fallback_false_이고_기존처럼_저장한다() {
        when(ml.contribution(anyMap())).thenReturn(Map.of(
                "available_amount", 1_100_000, "recommended_amount", 200_000, "capped", true,
                "round_unit", 50_000, "income_cap_ratio", 0.12,
                "risk", Map.of("default_probability", 0.065, "risk_level", "STABLE", "risk_label", "안정",
                        "safety_factor", 0.5, "source", "self_report")));

        Map<String, Object> res = service.contribution(7L, req());

        assertEquals(false, res.get("fallback"));
        assertFalse(res.containsKey("fallback_source"));
        verify(mapper).insertRiskScore(eq(7L), eq(new BigDecimal("0.0650")), eq("STABLE"), eq(new BigDecimal("0.50")), eq("N"));
        verify(mapper, never()).insertRiskScoreCopyOfLatest(anyLong());
    }

    @Test
    void 대체값_응답에는_연체_확률이_없다() {
        mlDown();
        when(mapper.selectLatestRiskScore(7L)).thenReturn(new RiskScoreRow("STABLE", new BigDecimal("0.50"), "N", "2026-11-27 10:00"));

        Map<String, Object> res = service.contribution(7L, req());

        assertFalse(res.containsKey("default_probability"));
        assertFalse(risk(res).containsKey("default_probability"));
        assertFalse(risk(res).containsKey("prob"));
        assertEquals(java.util.Set.of("risk_level", "risk_label", "safety_factor"), risk(res).keySet());
    }

    @Test
    void 대체값_저장이_실패해도_응답은_나간다() {
        mlDown();
        when(mapper.selectLatestRiskScore(7L)).thenReturn(new RiskScoreRow("CAUTION", new BigDecimal("0.35"), "N", "2026-11-27 10:00"));
        when(mapper.insertRiskScoreCopyOfLatest(7L)).thenThrow(new RuntimeException("db down"));

        Map<String, Object> res = service.contribution(7L, req());

        assertEquals("PREVIOUS", res.get("fallback_source"));
        assertEquals(200_000L, res.get("recommended_amount"));
    }

    // ---- 공용 계산식 RiskPolicy.recommend ----

    @Test
    void 가용액이_0_이하면_추천_0() {
        RiskPolicy.Recommendation r = RiskPolicy.recommend(2_000_000L, 1_500_000L, 300_000L, 200_000L, new BigDecimal("0.5"));
        assertEquals(0L, r.available());
        assertEquals(0L, r.recommended());
        assertFalse(r.capped());

        RiskPolicy.Recommendation minus = RiskPolicy.recommend(2_000_000L, 300_000L, 8_000_000L, 300_000L, new BigDecimal("0.35"));
        assertEquals(-6_600_000L, minus.available());
        assertEquals(0L, minus.recommended());
    }

    @Test
    void 상한_12퍼센트가_적용되면_capped_true() {
        RiskPolicy.Recommendation r = RiskPolicy.recommend(2_000_000L, 300_000L, 300_000L, 300_000L, new BigDecimal("0.5"));
        assertTrue(r.capped());
        assertEquals(200_000L, r.recommended());   // 상한 24만 → 20만
    }

    @Test
    void 추천액은_항상_5만원의_배수() {
        long[] incomes = {1_230_000L, 2_870_000L, 3_200_000L, 5_550_000L, 9_999_999L};
        String[] ks = {"0.5", "0.35", "0.2"};
        for (long income : incomes) {
            for (String k : ks) {
                for (long expense = 0; expense < income; expense += 370_000L) {
                    long rec = RiskPolicy.recommend(income, expense, 110_000L, 90_000L, new BigDecimal(k)).recommended();
                    assertEquals(0L, rec % 50_000L, () -> "income=" + income + " k=" + k);
                    assertTrue(rec >= 0);
                }
            }
        }
    }

    @Test
    void 등급_코드_라벨_계수는_한_곳에서_같다() {
        assertEquals("안정", RiskPolicy.labelOf("STABLE"));
        assertEquals("주의", RiskPolicy.labelOf("CAUTION"));
        assertEquals("위험", RiskPolicy.labelOf("RISK"));
        assertEquals("주의", RiskPolicy.labelOf("SAFE"));   // 알 수 없는 값은 CAUTION
        assertEquals(new BigDecimal("0.50"), RiskPolicy.safetyKOf("STABLE"));
        assertEquals(new BigDecimal("0.35"), RiskPolicy.safetyKOf("CAUTION"));
        assertEquals(new BigDecimal("0.20"), RiskPolicy.safetyKOf("RISK"));
    }
}
