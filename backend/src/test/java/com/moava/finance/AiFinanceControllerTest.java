package com.moava.finance;

import com.moava.finance.controller.AiFinanceController;
import com.moava.finance.controller.AiPayoutOrderController;
import com.moava.finance.controller.FinanceExceptionHandler;
import com.moava.finance.mapper.AiPayoutMapper;
import com.moava.finance.service.AiFinanceService;
import com.moava.finance.service.FinanceException;
import com.moava.finance.service.MlClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** AI 분석 중계 API: 로그인 회원 본인 확인·모임장 확인·ML 오류 안내 (ML 서버·DB 불필요) */
class AiFinanceControllerTest {

    MlClient ml;
    AiPayoutMapper mapper;
    MockMvc mvc;

    private static final String BODY = """
            {"monthly_income":3200000,"monthly_expense":2080000,"monthly_saving":350000,"emergency_fund":250000,            "self_report":{"late_count_12m":"0","max_late_period":"NONE","loan_count":"2","card_usage":"UNDER_30"}}""";

    @BeforeEach
    void setUp() {
        ml = mock(MlClient.class);
        mapper = mock(AiPayoutMapper.class);
        AiFinanceService service = new AiFinanceService(ml, mapper);
        mvc = MockMvcBuilders.standaloneSetup(new AiFinanceController(service), new AiPayoutOrderController(service))
                .setControllerAdvice(new FinanceExceptionHandler())
                .build();
    }

    private static UsernamePasswordAuthenticationToken auth(String id, String role) {
        return new UsernamePasswordAuthenticationToken(id, null, List.of(new SimpleGrantedAuthority(role)));
    }

    private static UsernamePasswordAuthenticationToken member(long id) {
        return auth(String.valueOf(id), "ROLE_MEMBER");
    }

    // ---- 추천 납입액 ----

    @Test
    @SuppressWarnings("unchecked")
    void 자가_응답으로_분석하고_본문의_member_id_는_ML_에_넘기지_않는다() throws Exception {
        when(ml.contribution(anyMap())).thenReturn(Map.of("recommended_amount", 150000, "available_amount", 520000));

        mvc.perform(post("/api/v1/finance/ai/contribution").principal(member(7))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("{", "{\"member_id\":999,")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommended_amount").value(150000));

        verify(ml).contribution((Map<String, Object>) argThat(m ->
                !((Map<String, Object>) m).containsKey("member_id")
                        && Map.of("late_count_12m", "0", "max_late_period", "NONE", "loan_count", "2", "card_usage", "UNDER_30")
                        .equals(((Map<String, Object>) m).get("self_report"))
                        && Long.valueOf(3200000).equals(((Map<String, Object>) m).get("monthly_income"))));
    }

    @Test
    void 자가_응답이_없으면_400() throws Exception {
        mvc.perform(post("/api/v1/finance/ai/contribution").principal(member(7))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthly_income\":3200000,\"monthly_expense\":2080000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_SELF_REPORT"));
        verifyNoInteractions(ml);
    }

    @Test
    void 자가_응답이_하나라도_빠지면_400() throws Exception {
        mvc.perform(post("/api/v1/finance/ai/contribution").principal(member(7))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.replace(",\"card_usage\":\"UNDER_30\"", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_SELF_REPORT"));
        verifyNoInteractions(ml);
    }

    @Test
    void 자가_응답_선택지가_목록에_없으면_400() throws Exception {
        mvc.perform(post("/api/v1/finance/ai/contribution").principal(member(7))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("\"UNDER_30\"", "\"10%\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_SELF_REPORT"));
        verifyNoInteractions(ml);
    }

    @Test
    void 추천_납입액_응답에서_본인_연체_확률은_빼고_등급만_준다() throws Exception {
        when(ml.contribution(anyMap())).thenReturn(Map.of(
                "recommended_amount", 150000, "available_amount", 520000,
                "risk", Map.of("default_probability", 0.123, "risk_level", "STABLE", "risk_label", "안정",
                        "safety_factor", 0.5, "source", "persona")));

        mvc.perform(post("/api/v1/finance/ai/contribution").principal(member(7))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommended_amount").value(150000))
                .andExpect(jsonPath("$.data.risk.risk_level").value("STABLE"))
                .andExpect(jsonPath("$.data.risk.risk_label").value("안정"))
                .andExpect(jsonPath("$.data.risk.safety_factor").value(0.5))
                .andExpect(jsonPath("$.data.risk.default_probability").doesNotExist())
                .andExpect(jsonPath("$.data.risk.source").doesNotExist())
                .andExpect(jsonPath("$.data.fallback").value(false));
    }

    @Test
    void 본인_응답에는_위험_등급_요인_factors_가_그대로_나온다() throws Exception {
        when(ml.contribution(anyMap())).thenReturn(Map.of(
                "recommended_amount", 150000, "available_amount", 520000,
                "risk", Map.of("default_probability", 0.191, "risk_level", "CAUTION", "risk_label", "주의",
                        "safety_factor", 0.35, "source", "self_report",
                        "factors", List.of(
                                Map.of("key", "late_count_12m", "question", "최근 1년간 30일 이상 연체한 횟수",
                                        "answer_label", "1회", "impact", 0.071),
                                Map.of("key", "card_usage", "question", "신용카드 한도 중 쓰고 있는 비율",
                                        "answer_label", "70~90%", "impact", 0.0367)))));

        mvc.perform(post("/api/v1/finance/ai/contribution").principal(member(7))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.risk.factors.length()").value(2))
                .andExpect(jsonPath("$.data.risk.factors[0].key").value("late_count_12m"))
                .andExpect(jsonPath("$.data.risk.factors[0].answer_label").value("1회"))
                .andExpect(jsonPath("$.data.risk.factors[1].impact").value(0.0367))
                .andExpect(jsonPath("$.data.risk.default_probability").doesNotExist());
    }

    @Test
    void 요인이_없으면_빈_목록_그대로() throws Exception {
        when(ml.contribution(anyMap())).thenReturn(Map.of(
                "recommended_amount", 150000, "available_amount", 520000,
                "risk", Map.of("risk_level", "STABLE", "risk_label", "안정", "safety_factor", 0.5, "factors", List.of())));

        mvc.perform(post("/api/v1/finance/ai/contribution").principal(member(7))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.risk.factors").isArray())
                .andExpect(jsonPath("$.data.risk.factors.length()").value(0));
    }

    @Test
    void 관리자_토큰은_403() throws Exception {
        mvc.perform(post("/api/v1/finance/ai/contribution").principal(auth("7", "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("MEMBER_ONLY"));
        verifyNoInteractions(ml);
    }

    @Test
    void 금액이_음수면_400() throws Exception {
        mvc.perform(post("/api/v1/finance/ai/contribution").principal(member(7))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("3200000", "-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_AMOUNT"));
        verifyNoInteractions(ml);
    }

    @Test
    void ML_서버가_꺼져_있어도_대체값으로_200_응답() throws Exception {
        when(ml.contribution(anyMap())).thenThrow(new FinanceException(HttpStatus.SERVICE_UNAVAILABLE,
                "ML_UNAVAILABLE", "분석 서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요."));

        // 3,200,000 − 2,080,000 − 350,000 − 250,000 = 520,000 × 0.35 = 182,000 → 150,000 (기록 없음 → 기본 '주의')
        mvc.perform(post("/api/v1/finance/ai/contribution").principal(member(7))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fallback").value(true))
                .andExpect(jsonPath("$.data.fallback_source").value("DEFAULT"))
                .andExpect(jsonPath("$.data.recommended_amount").value(150000))
                .andExpect(jsonPath("$.data.risk.risk_level").value("CAUTION"))
                .andExpect(jsonPath("$.data.risk.default_probability").doesNotExist())
                .andExpect(jsonPath("$.data.risk.factors").doesNotExist());   // 대체값은 요인을 알 수 없음
    }

    // ---- 정책 값 ----

    @Test
    void 정책_값만_골라서_주고_모델_내부_정보는_뺀다() throws Exception {
        when(ml.model()).thenReturn(Map.of(
                "round_unit", 50000, "income_cap_ratio", 0.12,
                "risk_levels", List.of(Map.of("code", "STABLE")),
                "features", List.of("A", "B"), "n_features", 2));

        mvc.perform(get("/api/v1/finance/ai/policy").principal(member(7)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.round_unit").value(50000))
                .andExpect(jsonPath("$.data.income_cap_ratio").value(0.12))
                .andExpect(jsonPath("$.data.risk_levels[0].code").value("STABLE"))
                .andExpect(jsonPath("$.data.features").doesNotExist())
                .andExpect(jsonPath("$.data.n_features").doesNotExist());
    }

    // ---- 수령 순서 ----

    @Test
    void 모임장은_DB_의_ACTIVE_회원으로_추천받는다() throws Exception {
        when(mapper.selectOwnerId(101L)).thenReturn(7L);
        when(mapper.selectActiveMemberIds(101L)).thenReturn(List.of(7L, 8L, 9L));
        when(ml.payoutOrder(anyList())).thenReturn(Map.of("rule", "r"));

        mvc.perform(post("/api/v1/groups/101/ai/payout-order").principal(member(7)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rule").value("r"));
        verify(ml).payoutOrder(List.of(7L, 8L, 9L));
    }

    @Test
    void 수령_순서_응답에는_다른_회원의_연체_확률이_없다() throws Exception {
        when(mapper.selectOwnerId(101L)).thenReturn(7L);
        when(mapper.selectActiveMemberIds(101L)).thenReturn(List.of(7L, 8L));
        when(ml.payoutOrder(anyList())).thenReturn(Map.of(
                "rule", "r",
                "order", List.of(
                        Map.of("order", 1, "member_id", 8, "risk", Map.of("default_probability", 0.41,
                                "risk_level", "RISK", "risk_label", "위험", "safety_factor", 0.2, "source", "persona",
                                "factors", List.of(Map.of("key", "late_count_12m", "answer_label", "3회 이상")))),
                        Map.of("order", 2, "member_id", 7, "risk", Map.of("default_probability", 0.05,
                                "risk_level", "STABLE", "risk_label", "안정", "safety_factor", 0.5, "source", "persona")))));

        mvc.perform(post("/api/v1/groups/101/ai/payout-order").principal(member(7)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rule").value("r"))
                .andExpect(jsonPath("$.data.order[0].order").value(1))
                .andExpect(jsonPath("$.data.order[0].member_id").value(8))
                .andExpect(jsonPath("$.data.order[0].risk.risk_level").value("RISK"))
                .andExpect(jsonPath("$.data.order[0].risk.risk_label").value("위험"))
                .andExpect(jsonPath("$.data.order[0].risk.default_probability").doesNotExist())
                .andExpect(jsonPath("$.data.order[0].risk.safety_factor").doesNotExist())
                .andExpect(jsonPath("$.data.order[0].risk.source").doesNotExist())
                .andExpect(jsonPath("$.data.order[0].risk.factors").doesNotExist())   // 다른 회원의 요인은 보여 주지 않는다
                .andExpect(jsonPath("$.data.order[1].member_id").value(7))
                .andExpect(jsonPath("$.data.order[1].risk.default_probability").doesNotExist());
    }

    @Test
    void 남의_모임은_403() throws Exception {
        when(mapper.selectOwnerId(101L)).thenReturn(2L);

        mvc.perform(post("/api/v1/groups/101/ai/payout-order").principal(member(7)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.message").value("모임장만 이용할 수 있어요."));
        verify(mapper, never()).selectActiveMemberIds(anyLong());
        verifyNoInteractions(ml);
    }

    @Test
    void 관리자_토큰은_수령_순서도_403() throws Exception {
        mvc.perform(post("/api/v1/groups/101/ai/payout-order").principal(auth("7", "ROLE_ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("MEMBER_ONLY"));
        verifyNoInteractions(mapper, ml);
    }

    @Test
    void 없는_모임은_404() throws Exception {
        when(mapper.selectOwnerId(101L)).thenReturn(null);

        mvc.perform(post("/api/v1/groups/101/ai/payout-order").principal(member(7)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GROUP_NOT_FOUND"));
    }

    @Test
    void 회원이_2명_미만이면_400() throws Exception {
        when(mapper.selectOwnerId(101L)).thenReturn(7L);
        when(mapper.selectActiveMemberIds(101L)).thenReturn(List.of(7L));

        mvc.perform(post("/api/v1/groups/101/ai/payout-order").principal(member(7)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("추천하려면 회원이 2명 이상 필요해요"));
        verifyNoInteractions(ml);
    }
}
