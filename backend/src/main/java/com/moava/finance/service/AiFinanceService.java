package com.moava.finance.service;

import com.moava.finance.dto.AiContributionRequest;
import com.moava.finance.dto.RiskScoreRow;
import com.moava.finance.mapper.AiPayoutMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 금융 분석 중계 (MOA-34). 회원 번호는 항상 로그인 토큰에서 온 값만 쓴다.
 * <ul>
 *   <li>추천 납입액: 본인 것만. 위험 등급은 자가 응답 4문항으로 ML 이 계산 (MOA-98)</li>
 *   <li>정책 값: 모델 내부 정보(지표 목록 등)는 빼고 화면에 필요한 값만</li>
 *   <li>수령 순서: 모임장만, 회원 목록은 DB 에서 직접 조회</li>
 * </ul>
 */
@Service
public class AiFinanceService {

    private static final Logger log = LoggerFactory.getLogger(AiFinanceService.class);

    /** 대체값 출처: 직전 RISK_SCORE 재사용 / 기록이 없어 기본 '주의' */
    static final String FALLBACK_PREVIOUS = "PREVIOUS";
    static final String FALLBACK_DEFAULT = "DEFAULT";

    /** 정책 값으로 내보낼 ML /risk/model 항목 */
    static final List<String> POLICY_KEYS = List.of("round_unit", "income_cap_ratio", "risk_levels");

    private final MlClient ml;
    private final AiPayoutMapper mapper;
    private final FinanceProfileService profileService;

    public AiFinanceService(MlClient ml, AiPayoutMapper mapper) {
        this(ml, mapper, null);
    }

    @Autowired
    public AiFinanceService(MlClient ml, AiPayoutMapper mapper, FinanceProfileService profileService) {
        this.ml = ml;
        this.mapper = mapper;
        this.profileService = profileService;
    }

    /**
     * 위험 정보 중 회원에게 보여도 되는 항목 (명세서 F09: 연체 확률은 관리자·등급 산출에만, 회원 API 는 등급만).
     * 안전계수는 등급마다 정해진 정책 값(/policy 에도 공개)이라 본인 화면에서 계산 근거로 보여 준다.
     * factors(MOA-101)는 본인이 답한 자가 응답 중 등급을 높인 요인이라 본인 응답에만 준다(DB 에 저장하지 않음).
     */
    static final List<String> OWN_RISK_KEYS = List.of("risk_level", "risk_label", "safety_factor", "factors");
    /** 다른 회원 위험 정보는 등급만 */
    static final List<String> OTHERS_RISK_KEYS = List.of("risk_level", "risk_label");

    /**
     * 추천 납입액. 응답 모양은 ML 과 같게 두되, risk 에서 연체 확률 등은 뺀다.
     * 응답에는 항상 fallback(true/false)이 있다. ML 서버에 연결하지 못하면(ML_UNAVAILABLE) 503 대신
     * 서버에서 같은 식으로 계산한 대체값을 준다 (F09 예외: 직전 RISK_SCORE 재사용, 없으면 CAUTION k=0.35).
     * 입력 검증(금액·자가 응답 4문항)은 대체값을 쓸 때도 먼저 한다.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> contribution(long memberId, AiContributionRequest req) {
        if (req == null) {
            throw invalidAmount();
        }
        // 자가 응답은 민감한 신용 정보 — 값은 ML 에만 넘기고 DB·로그에 남기지 않는다
        if (req.selfReport() == null || !req.selfReport().isValid()) {
            throw new FinanceException(HttpStatus.BAD_REQUEST, "INVALID_SELF_REPORT", "신용 관련 질문 4개에 모두 답해 주세요.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("self_report", req.selfReport().toMl());
        body.put("monthly_income", required(req.monthlyIncome()));
        body.put("monthly_expense", required(req.monthlyExpense()));
        body.put("monthly_saving", optional(req.monthlySaving()));
        body.put("emergency_fund", optional(req.emergencyFund()));
        Map<String, Object> raw;
        try {
            raw = ml.contribution(body);
        } catch (FinanceException e) {
            if (!"ML_UNAVAILABLE".equals(e.getCode())) {
                throw e;   // ML_BAD_REQUEST 등 입력 오류는 그대로
            }
            return fallback(memberId, body, e.getCode());
        }
        Map<String, Object> result = new LinkedHashMap<>(raw);
        Object rawRisk = result.get("risk");
        if (profileService != null && rawRisk instanceof Map<?, ?> riskMap) {
            profileService.recordRiskScore(memberId, (Map<String, Object>) riskMap);
        }
        result.put("risk", pick(rawRisk, OWN_RISK_KEYS));
        result.put("fallback", false);
        return result;
    }

    /**
     * ML 장애 대체값. 직전 RISK_SCORE 가 있으면 그 등급·안전계수(PREVIOUS), 없으면 CAUTION·0.35(DEFAULT).
     * 계산식은 ML 과 같다(RiskPolicy.recommend). 응답에 연체 확률은 넣지 않는다.
     * 로그에는 회원 번호·출처·원인 코드만 남긴다(금액·자가 응답·내부 키 X).
     */
    private Map<String, Object> fallback(long memberId, Map<String, Object> body, String cause) {
        RiskScoreRow previous = profileService == null ? null : profileService.latestRiskScore(memberId);
        String source = previous != null ? FALLBACK_PREVIOUS : FALLBACK_DEFAULT;
        String tier = previous != null ? RiskPolicy.normalizeTier(previous.tier()) : RiskPolicy.CAUTION;
        BigDecimal safetyK = previous != null && previous.safetyK() != null ? previous.safetyK() : RiskPolicy.safetyKOf(tier);

        RiskPolicy.Recommendation r = RiskPolicy.recommend(
                (long) body.get("monthly_income"), (long) body.get("monthly_expense"),
                (long) body.get("monthly_saving"), (long) body.get("emergency_fund"), safetyK);

        if (previous != null) {
            // 직전 기록을 FALLBACK_YN='Y' 로 한 줄 더 남긴다(확률·등급·계수 그대로 복사)
            profileService.recordFallbackCopy(memberId);
        }
        // DEFAULT 는 RISK_SCORE 에 저장하지 않는다: 연체 확률을 알 수 없어서 값을 지어내지 않기 위해서다.
        // 그래서 이 회원은 "분석 기록 없음"으로 남아 수령 순서 맨 뒤 회차·모임 건강도 평균 제외 규칙을 그대로 따른다.
        log.warn("AI 분석 대체값 사용 memberId={} fallback_source={} cause={}", memberId, source, cause);

        Map<String, Object> risk = new LinkedHashMap<>();
        risk.put("risk_level", tier);
        risk.put("risk_label", RiskPolicy.labelOf(tier));
        risk.put("safety_factor", safetyK.doubleValue());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available_amount", r.available());
        result.put("recommended_amount", r.recommended());
        result.put("capped", r.capped());
        result.put("round_unit", RiskPolicy.ROUND_UNIT);
        result.put("income_cap_ratio", RiskPolicy.INCOME_CAP_RATIO.doubleValue());
        result.put("risk", risk);
        result.put("fallback", true);
        result.put("fallback_source", source);
        return result;
    }

    /** 화면 정책 값: round_unit(원), income_cap_ratio, risk_levels */
    public Map<String, Object> policy() {
        Map<String, Object> model = ml.model();
        Map<String, Object> policy = new LinkedHashMap<>();
        for (String key : POLICY_KEYS) {
            if (model.containsKey(key)) {
                policy.put(key, model.get(key));
            }
        }
        return policy;
    }

    /** 곗돈 수령 순서 추천. 모임장만, 모임의 ACTIVE 회원 전원 대상. */
    public Map<String, Object> payoutOrder(long memberId, long groupId) {
        Long ownerId = mapper.selectOwnerId(groupId);
        if (ownerId == null) {
            throw new FinanceException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "모임을 찾을 수 없어요.");
        }
        if (ownerId != memberId) {
            throw new FinanceException(HttpStatus.FORBIDDEN, "LEADER_ONLY", "모임장만 이용할 수 있어요.");
        }
        List<Long> members = mapper.selectActiveMemberIds(groupId);
        if (members == null || members.size() < 2) {
            throw new FinanceException(HttpStatus.BAD_REQUEST, "NOT_ENOUGH_MEMBERS", "추천하려면 회원이 2명 이상 필요해요");
        }
        Map<String, Object> result = ml.payoutOrder(members);
        // 모임장에게 다른 회원의 연체 확률이 보이지 않도록 순서·회원 번호·등급만 남긴다
        List<Map<String, Object>> order = new ArrayList<>();
        if (result.get("order") instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("order", m.get("order"));
                    row.put("member_id", m.get("member_id"));
                    row.put("risk", pick(m.get("risk"), OTHERS_RISK_KEYS));
                    order.add(row);
                }
            }
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        safe.put("order", order);
        safe.put("rule", result.get("rule"));
        return safe;
    }

    /** risk 객체에서 허용된 항목만 골라 새 Map 으로 (없으면 빈 Map) */
    private static Map<String, Object> pick(Object risk, List<String> keys) {
        Map<String, Object> picked = new LinkedHashMap<>();
        if (risk instanceof Map<?, ?> m) {
            for (String key : keys) {
                if (m.containsKey(key)) {
                    picked.put(key, m.get(key));
                }
            }
        }
        return picked;
    }

    private static long required(Long amount) {
        if (amount == null || amount < 0) {
            throw invalidAmount();
        }
        return amount;
    }

    private static long optional(Long amount) {
        return amount == null ? 0 : required(amount);
    }

    private static FinanceException invalidAmount() {
        return new FinanceException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "금액을 확인해 주세요.");
    }
}
