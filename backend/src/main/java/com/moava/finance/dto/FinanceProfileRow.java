package com.moava.finance.dto;

/** Oracle FINANCE_PROFILE 테이블 1건 조회 행 (금액 단위: 원). */
public record FinanceProfileRow(
        long income,
        long fixedExp,
        long variableExp,
        long saving,
        long reserve,
        long goalAmount,
        String consentYn,
        String consentAt,
        int journeyStep,
        String updatedAt
) {
}
