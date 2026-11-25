package com.moava.finance.service;

import com.moava.finance.dto.AiContributionRequest;
import com.moava.finance.mapper.AiPayoutMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 금융 분석 중계 (MOA-34). 회원 번호는 항상 로그인 토큰에서 온 값만 ML 에 넘긴다.
 * <ul>
 *   <li>추천 납입액: 본인 것만</li>
 *   <li>정책 값: 모델 내부 정보(지표 목록 등)는 빼고 화면에 필요한 값만</li>
 *   <li>수령 순서: 모임장만, 회원 목록은 DB 에서 직접 조회</li>
 * </ul>
 */
@Service
public class AiFinanceService {

    /** 정책 값으로 내보낼 ML /risk/model 항목 */
    static final List<String> POLICY_KEYS = List.of("round_unit", "income_cap_ratio", "risk_levels");

    private final MlClient ml;
    private final AiPayoutMapper mapper;

    public AiFinanceService(MlClient ml, AiPayoutMapper mapper) {
        this.ml = ml;
        this.mapper = mapper;
    }

    /**
     * 위험 정보 중 회원에게 보여도 되는 항목 (명세서 F09: 연체 확률은 관리자·등급 산출에만, 회원 API 는 등급만).
     * 안전계수는 등급마다 정해진 정책 값(/policy 에도 공개)이라 본인 화면에서 계산 근거로 보여 준다.
     */
    static final List<String> OWN_RISK_KEYS = List.of("risk_level", "risk_label", "safety_factor");
    /** 다른 회원 위험 정보는 등급만 */
    static final List<String> OTHERS_RISK_KEYS = List.of("risk_level", "risk_label");

    /** 추천 납입액. 응답 모양은 ML 과 같게 두되, risk 에서 연체 확률 등은 뺀다. */
    public Map<String, Object> contribution(long memberId, AiContributionRequest req) {
        if (req == null) {
            throw invalidAmount();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("member_id", memberId);
        body.put("monthly_income", required(req.monthlyIncome()));
        body.put("monthly_expense", required(req.monthlyExpense()));
        body.put("monthly_saving", optional(req.monthlySaving()));
        body.put("emergency_fund", optional(req.emergencyFund()));
        Map<String, Object> result = new LinkedHashMap<>(ml.contribution(body));
        result.put("risk", pick(result.get("risk"), OWN_RISK_KEYS));
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
