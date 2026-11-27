package com.moava.finance.service;

import com.moava.finance.dto.FinanceProfileResponse;
import com.moava.finance.dto.FinanceProfileRow;
import com.moava.finance.dto.FinanceProfileUpsertRequest;
import com.moava.finance.dto.RiskScoreRow;
import com.moava.finance.mapper.FinanceProfileMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;

/**
 * F05 금융 프로필(FINANCE_PROFILE) + 위험 등급 이력(RISK_SCORE) DB 저장·조회 서비스.
 * - 회원당 FINANCE_PROFILE 1건 Upsert, 맞춤 추천 단계(1~4) 유지
 * - AI 분석(/api/v1/finance/ai/contribution) 완료 시 RISK_SCORE 이력 누적
 * - 재로그인 시 저장된 FINANCE_PROFILE + 최신 RISK_SCORE 로 추천 납입액·위험 등급 즉시 복원
 */
@Service
public class FinanceProfileService {

    private static final Logger log = LoggerFactory.getLogger(FinanceProfileService.class);
    private static final BigDecimal INCOME_CAP_RATIO = new BigDecimal("0.12");
    private static final BigDecimal ROUND_UNIT = new BigDecimal("50000");
    private static final Set<String> VALID_TIERS = Set.of("STABLE", "CAUTION", "RISK");

    private final FinanceProfileMapper mapper;

    public FinanceProfileService(FinanceProfileMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public FinanceProfileResponse getProfile(long memberId) {
        FinanceProfileRow row = mapper.selectProfile(memberId);
        if (row == null) {
            return FinanceProfileResponse.empty();
        }
        boolean consent = "Y".equalsIgnoreCase(row.consentYn());
        FinanceProfileResponse.SavedAssessment assessment = null;
        if (consent) {
            RiskScoreRow risk = mapper.selectLatestRiskScore(memberId);
            if (risk != null) {
                assessment = buildAssessment(row, risk);
            }
        }
        return new FinanceProfileResponse(
                true,
                row.income(),
                row.fixedExp(),
                row.variableExp(),
                row.saving(),
                row.reserve(),
                row.goalAmount(),
                consent,
                row.consentAt(),
                clampStep(row.journeyStep()),
                row.updatedAt(),
                assessment
        );
    }

    @Transactional
    public FinanceProfileResponse saveProfile(long memberId, FinanceProfileUpsertRequest req) {
        if (req == null) {
            throw new FinanceException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "금융 정보를 입력해 주세요.");
        }
        long income = requireNonNegative(req.income());
        long fixedExp = requireNonNegative(req.fixedExp());
        long variableExp = requireNonNegative(req.variableExp());
        long saving = requireNonNegative(req.saving());
        long reserve = requireNonNegative(req.reserve());
        long goalAmount = requireNonNegative(req.goalAmount());

        boolean consent = Boolean.TRUE.equals(req.consent());
        if (!consent) {
            throw new FinanceException(HttpStatus.BAD_REQUEST, "CONSENT_REQUIRED", "금융정보 분석 이용에 동의해 주세요.");
        }

        int step = req.journeyStep() != null ? validateStep(req.journeyStep()) : 2;
        mapper.upsertProfile(memberId, income, fixedExp, variableExp, saving, reserve, goalAmount, "Y", step);
        return getProfile(memberId);
    }

    @Transactional
    public int updateJourneyStep(long memberId, Integer step) {
        int validStep = validateStep(step);
        mapper.updateJourneyStep(memberId, validStep);
        return validStep;
    }

    /** ML /risk/contribution 결과의 risk 정보를 Oracle RISK_SCORE 에 저장한다. */
    @Transactional
    public void recordRiskScore(long memberId, Map<String, Object> rawRisk) {
        if (rawRisk == null || rawRisk.isEmpty()) {
            return;
        }
        try {
            String tier = String.valueOf(rawRisk.get("risk_level")).trim().toUpperCase();
            if (!VALID_TIERS.contains(tier)) {
                log.warn("RISK_SCORE 저장 생략 — 알 수 없는 tier={} memberId={}", tier, memberId);
                return;
            }
            BigDecimal prob = toDecimal(rawRisk.get("default_probability"));
            BigDecimal safetyK = toDecimal(rawRisk.get("safety_factor"));
            if (prob == null || safetyK == null) {
                log.warn("RISK_SCORE 저장 생략 — prob/safetyK 누락 memberId={}", memberId);
                return;
            }
            if (prob.compareTo(BigDecimal.ZERO) < 0) prob = BigDecimal.ZERO;
            if (prob.compareTo(BigDecimal.ONE) > 0) prob = BigDecimal.ONE;
            prob = prob.setScale(4, RoundingMode.HALF_UP);
            safetyK = safetyK.setScale(2, RoundingMode.HALF_UP);

            mapper.insertRiskScore(memberId, prob, tier, safetyK, "N");
        } catch (Exception e) {
            // RISK_SCORE 저장 실패가 AI 분석 응답 자체를 막지 않도록 경고만 남김
            log.warn("RISK_SCORE 저장 실패 memberId={} cause={}", memberId, e.getMessage());
        }
    }

    static FinanceProfileResponse.SavedAssessment buildAssessment(FinanceProfileRow profile, RiskScoreRow risk) {
        long available = profile.income() - profile.fixedExp() - profile.variableExp() - profile.saving() - profile.reserve();
        BigDecimal safetyK = risk.safetyK() != null ? risk.safetyK() : defaultSafetyK(risk.tier());
        long recommended = 0L;
        boolean capped = false;
        if (available > 0) {
            BigDecimal byRisk = BigDecimal.valueOf(available).multiply(safetyK);
            BigDecimal cap = BigDecimal.valueOf(profile.income()).multiply(INCOME_CAP_RATIO);
            capped = byRisk.compareTo(cap) > 0;
            BigDecimal raw = capped ? cap : byRisk;
            BigDecimal units = raw.divide(ROUND_UNIT, 0, RoundingMode.FLOOR);
            recommended = Math.max(0L, units.multiply(ROUND_UNIT).longValue());
        }
        String tier = risk.tier() == null ? "CAUTION" : risk.tier().trim().toUpperCase();
        return new FinanceProfileResponse.SavedAssessment(
                available,
                recommended,
                capped,
                tier,
                labelOf(tier),
                safetyK.doubleValue(),
                risk.scoredAt()
        );
    }

    private static BigDecimal defaultSafetyK(String tier) {
        if ("STABLE".equalsIgnoreCase(tier)) return new BigDecimal("0.50");
        if ("RISK".equalsIgnoreCase(tier)) return new BigDecimal("0.20");
        return new BigDecimal("0.35");
    }

    private static String labelOf(String tier) {
        if ("STABLE".equalsIgnoreCase(tier)) return "안정";
        if ("RISK".equalsIgnoreCase(tier)) return "위험";
        return "주의";
    }

    private static long requireNonNegative(Long value) {
        if (value == null || value < 0) {
            throw new FinanceException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "금액을 확인해 주세요.");
        }
        return value;
    }

    private static int validateStep(Integer step) {
        if (step == null || step < 1 || step > 4) {
            throw new FinanceException(HttpStatus.BAD_REQUEST, "INVALID_STEP", "진행 단계는 1~4 사이여야 합니다.");
        }
        return step;
    }

    private static int clampStep(int step) {
        return Math.max(1, Math.min(4, step));
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) {
            double d = n.doubleValue();
            if (!Double.isFinite(d)) return null;
            return BigDecimal.valueOf(d);
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
