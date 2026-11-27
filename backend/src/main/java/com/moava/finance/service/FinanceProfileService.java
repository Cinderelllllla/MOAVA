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
    private static final Set<String> VALID_TIERS = RiskPolicy.TIERS;

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

    /** 회원의 최신 RISK_SCORE (없으면 null). ML 장애 시 대체값(PREVIOUS)에 쓴다. 연체 확률은 담겨 있지 않다. */
    @Transactional(readOnly = true)
    public RiskScoreRow latestRiskScore(long memberId) {
        return mapper.selectLatestRiskScore(memberId);
    }

    /**
     * F09 ML 장애 시(PREVIOUS): 직전 RISK_SCORE 행의 PROB·TIER·SAFETY_K 를 그대로 복사해 FALLBACK_YN='Y' 로 한 줄 더 쌓는다.
     * 복사는 DB 안에서 INSERT ... SELECT 로 해서 연체 확률이 자바 코드·응답을 거치지 않는다.
     * 저장 실패가 추천 응답을 막지 않도록 경고만 남긴다.
     */
    @Transactional
    public void recordFallbackCopy(long memberId) {
        try {
            mapper.insertRiskScoreCopyOfLatest(memberId);
        } catch (Exception e) {
            log.warn("RISK_SCORE 대체값 저장 실패 memberId={} cause={}", memberId, e.getClass().getSimpleName());
        }
    }

    static FinanceProfileResponse.SavedAssessment buildAssessment(FinanceProfileRow profile, RiskScoreRow risk) {
        String tier = RiskPolicy.normalizeTier(risk.tier());
        BigDecimal safetyK = risk.safetyK() != null ? risk.safetyK() : RiskPolicy.safetyKOf(tier);
        RiskPolicy.Recommendation r = RiskPolicy.recommend(
                profile.income(), profile.fixedExp() + profile.variableExp(), profile.saving(), profile.reserve(), safetyK);
        return new FinanceProfileResponse.SavedAssessment(
                r.available(),
                r.recommended(),
                r.capped(),
                tier,
                RiskPolicy.labelOf(tier),
                safetyK.doubleValue(),
                risk.scoredAt()
        );
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
