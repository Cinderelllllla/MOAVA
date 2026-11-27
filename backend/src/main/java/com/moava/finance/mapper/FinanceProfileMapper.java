package com.moava.finance.mapper;

import com.moava.finance.dto.FinanceProfileRow;
import com.moava.finance.dto.RiskScoreRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/** F05 금융 프로필(FINANCE_PROFILE) 및 위험 등급 이력(RISK_SCORE) 매퍼. */
@Mapper
public interface FinanceProfileMapper {

    FinanceProfileRow selectProfile(@Param("memberId") long memberId);

    int upsertProfile(
            @Param("memberId") long memberId,
            @Param("income") long income,
            @Param("fixedExp") long fixedExp,
            @Param("variableExp") long variableExp,
            @Param("saving") long saving,
            @Param("reserve") long reserve,
            @Param("goalAmount") long goalAmount,
            @Param("consentYn") String consentYn,
            @Param("journeyStep") int journeyStep
    );

    int updateJourneyStep(@Param("memberId") long memberId, @Param("journeyStep") int journeyStep);

    RiskScoreRow selectLatestRiskScore(@Param("memberId") long memberId);

    int insertRiskScore(
            @Param("memberId") long memberId,
            @Param("prob") BigDecimal prob,
            @Param("tier") String tier,
            @Param("safetyK") BigDecimal safetyK,
            @Param("fallbackYn") String fallbackYn
    );
}
