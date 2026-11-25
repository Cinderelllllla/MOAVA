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
            {"monthly_income":3200000,"monthly_expense":2080000,"monthly_saving":350000,"emergency_fund":250000}""";

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
    void 회원은_토큰의_회원_번호로_분석된다_본문의_member_id_는_무시() throws Exception {
        when(ml.contribution(anyMap())).thenReturn(Map.of("recommended_amount", 150000, "available_amount", 520000));

        mvc.perform(post("/api/v1/finance/ai/contribution").principal(member(7))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("{", "{\"member_id\":999,")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommended_amount").value(150000));

        verify(ml).contribution((Map<String, Object>) argThat(m ->
                Long.valueOf(7).equals(((Map<String, Object>) m).get("member_id"))
                        && Long.valueOf(3200000).equals(((Map<String, Object>) m).get("monthly_income"))));
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
                .andExpect(jsonPath("$.data.risk.source").doesNotExist());
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
    void ML_서버가_꺼져_있으면_503_안내() throws Exception {
        when(ml.contribution(anyMap())).thenThrow(new FinanceException(HttpStatus.SERVICE_UNAVAILABLE,
                "ML_UNAVAILABLE", "분석 서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요."));

        mvc.perform(post("/api/v1/finance/ai/contribution").principal(member(7))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("ML_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.message").value("분석 서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요."));
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
                                "risk_level", "RISK", "risk_label", "위험", "safety_factor", 0.2, "source", "persona")),
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
