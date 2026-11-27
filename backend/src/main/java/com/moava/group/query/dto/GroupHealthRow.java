package com.moava.group.query.dto;

/** F11 모임별 건강도 점수(GYE_MEMBER + RISK_SCORE 기반) 조회 DTO */
public record GroupHealthRow(
        Long groupId,
        Integer healthScore
) {}
