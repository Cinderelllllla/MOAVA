package com.moava.finance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 추천 납입액 요청 (원 단위). 필드 이름은 ML 서버와 같은 snake_case.
 * member_id 는 받지 않는다 — 보내도 무시한다.
 * 위험 등급은 자가 응답 4문항(self_report, 필수)으로 ML 이 계산한다 (F09, MOA-98).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiContributionRequest(
        @JsonProperty("monthly_income") Long monthlyIncome,
        @JsonProperty("monthly_expense") Long monthlyExpense,
        @JsonProperty("monthly_saving") Long monthlySaving,
        @JsonProperty("emergency_fund") Long emergencyFund,
        @JsonProperty("self_report") SelfReportRequest selfReport) {
}
