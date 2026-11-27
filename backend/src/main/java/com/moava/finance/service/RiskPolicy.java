package com.moava.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * F08·F09 추천 납입액·위험 등급 정책 값과 계산식 (ML 서버 ml/src/common/config.py · recommend_amount 와 같은 값·같은 식).
 * 저장된 결과 복원(FinanceProfileService)과 ML 장애 시 대체값(AiFinanceService) 이 이 한 곳을 같이 쓴다.
 * 등급 코드는 DB RISK_SCORE.TIER 와 같은 STABLE / CAUTION / RISK.
 */
public final class RiskPolicy {

    private RiskPolicy() {
    }

    /** 추천액 절사 단위(원) */
    public static final long ROUND_UNIT = 50_000L;
    /** 추천액 상한: 월수입 대비 비율 */
    public static final BigDecimal INCOME_CAP_RATIO = new BigDecimal("0.12");

    public static final String STABLE = "STABLE";
    public static final String CAUTION = "CAUTION";
    public static final String RISK = "RISK";
    public static final Set<String> TIERS = Set.of(STABLE, CAUTION, RISK);

    /** 알 수 없는 값이면 CAUTION */
    public static String normalizeTier(String tier) {
        String t = tier == null ? "" : tier.trim().toUpperCase();
        return TIERS.contains(t) ? t : CAUTION;
    }

    public static String labelOf(String tier) {
        return switch (normalizeTier(tier)) {
            case STABLE -> "안정";
            case RISK -> "위험";
            default -> "주의";
        };
    }

    /** 등급별 안전계수: 안정 0.5 / 주의 0.35 / 위험 0.2 */
    public static BigDecimal safetyKOf(String tier) {
        return switch (normalizeTier(tier)) {
            case STABLE -> new BigDecimal("0.50");
            case RISK -> new BigDecimal("0.20");
            default -> new BigDecimal("0.35");
        };
    }

    /** 계산 결과 (원 단위) */
    public record Recommendation(long available, long recommended, boolean capped) {
    }

    /**
     * 가용액 = 월수입 − 월지출 − 저축 − 비상예비금.
     * 추천액 = min(가용액 × 안전계수, 월수입 × 12%) 를 5만 원 단위로 내림. 가용액이 0 이하면 0.
     */
    public static Recommendation recommend(long income, long expense, long saving, long reserve, BigDecimal safetyK) {
        long available = income - expense - saving - reserve;
        if (available <= 0) {
            return new Recommendation(available, 0L, false);
        }
        BigDecimal byRisk = BigDecimal.valueOf(available).multiply(safetyK);
        BigDecimal cap = BigDecimal.valueOf(income).multiply(INCOME_CAP_RATIO);
        boolean capped = byRisk.compareTo(cap) > 0;
        BigDecimal raw = capped ? cap : byRisk;
        BigDecimal unit = BigDecimal.valueOf(ROUND_UNIT);
        long recommended = raw.divide(unit, 0, RoundingMode.FLOOR).multiply(unit).longValue();
        return new Recommendation(available, Math.max(0L, recommended), capped);
    }
}
