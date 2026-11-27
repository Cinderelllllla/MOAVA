package com.moava.finance.dto;

import java.math.BigDecimal;

/** Oracle RISK_SCORE 테이블 최신 1건 조회 행 (회원 응답에 연체 확률 PROB 는 노출하지 않음). */
public record RiskScoreRow(
        String tier,
        BigDecimal safetyK,
        String fallbackYn,
        String scoredAt
) {
}
