package com.moava.finance.dto;

/** PUT /api/v1/finance/profile 요청 본문 (금액 단위: 원). */
public record FinanceProfileUpsertRequest(
        Long income,
        Long fixedExp,
        Long variableExp,
        Long saving,
        Long reserve,
        Long goalAmount,
        Boolean consent,
        Integer journeyStep
) {
}
