package com.moava.finance.service;

import com.moava.finance.dto.AiContributionRequest;
import com.moava.finance.mapper.AiPayoutMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

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

    /** 추천 납입액. ML 응답을 그대로 돌려준다 (프론트 파싱 코드 유지). */
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
        return ml.contribution(body);
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
        return ml.payoutOrder(members);
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
