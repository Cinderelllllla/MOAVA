package com.moava.finance.dto;

/**
 * GET / PUT /api/v1/finance/profile 응답 (금액 단위: 원).
 * - exists=false 이면 아직 저장된 금융 프로필이 없는 상태
 * - assessment 는 저장된 FINANCE_PROFILE + 최신 RISK_SCORE 가 모두 있을 때 채워진다 (연체 확률 PROB 미포함)
 */
public record FinanceProfileResponse(
        boolean exists,
        Long income,
        Long fixedExp,
        Long variableExp,
        Long saving,
        Long reserve,
        Long goalAmount,
        boolean consent,
        String consentAt,
        int journeyStep,
        String updatedAt,
        SavedAssessment assessment
) {
    public record SavedAssessment(
            long availableAmount,
            long recommendedAmount,
            boolean capped,
            String riskLevel,
            String riskLabel,
            double safetyFactor,
            String scoredAt
    ) {
    }

    public static FinanceProfileResponse empty() {
        return new FinanceProfileResponse(
                false, null, null, null, null, null, null,
                false, null, 1, null, null
        );
    }
}
