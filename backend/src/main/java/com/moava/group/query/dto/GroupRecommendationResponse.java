package com.moava.group.query.dto;

import java.util.List;

/** F11 추천액·지역 기반 모임 추천 응답 DTO */
public record GroupRecommendationResponse(
        long recommendedAmount,
        long maxAmount,
        String regionSido,
        String regionSigungu,
        String regionDong,
        String matchedScope,
        boolean expandedToSido,
        List<RecommendedGroupResponse> items
) {}
