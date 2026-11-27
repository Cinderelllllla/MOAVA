package com.moava.finance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 추천 납입액 요청 (원 단위). 필드 이름은 ML 서버와 같은 snake_case.
 * member_id 는 받지 않는다 — 보내도 무시하고, 서버가 로그인 토큰의 회원 번호를 넣는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiContributionRequest(
        @JsonProperty("monthly_income") Long monthlyIncome,
        @JsonProperty("monthly_expense") Long monthlyExpense,
        @JsonProperty("monthly_saving") Long monthlySaving,
        @JsonProperty("emergency_fund") Long emergencyFund) {
}
