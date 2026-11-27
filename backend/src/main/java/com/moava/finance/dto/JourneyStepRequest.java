package com.moava.finance.dto;

/** PATCH /api/v1/finance/profile/step 요청 본문 (맞춤 추천 1~4단계). */
public record JourneyStepRequest(Integer step) {
}
