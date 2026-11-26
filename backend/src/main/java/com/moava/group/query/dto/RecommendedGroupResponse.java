package com.moava.group.query.dto;

import java.math.BigDecimal;

/** F11 추천 모임 단건 응답 DTO (PublicGroupResponse 호환 + 건강도/추천액 차이) */
public record RecommendedGroupResponse(
        Long groupId,
        String title,
        Long ownerId,
        String ownerName,
        String status,
        String purpose,
        String regionSido,
        String regionSigungu,
        String regionDong,
        BigDecimal lat,
        BigDecimal lng,
        Long amount,
        Integer maxMembers,
        Integer currentMembers,
        Integer months,
        Integer payDay,
        BigDecimal assumedRate,
        String visibility,
        String description,
        String rulesTitle,
        String rulesBody,
        boolean orderConfirmed,
        String role,
        Integer healthScore,
        Long amountDiff
) {
    public static RecommendedGroupResponse of(
            PublicGroupResponse base,
            int healthScore,
            long amountDiff
    ) {
        return new RecommendedGroupResponse(
                base.groupId(),
                base.title(),
                base.ownerId(),
                base.ownerName(),
                base.status(),
                base.purpose(),
                base.regionSido(),
                base.regionSigungu(),
                base.regionDong(),
                base.lat(),
                base.lng(),
                base.amount(),
                base.maxMembers(),
                base.currentMembers(),
                base.months(),
                base.payDay(),
                base.assumedRate(),
                base.visibility(),
                base.description(),
                base.rulesTitle(),
                base.rulesBody(),
                base.orderConfirmed(),
                base.role(),
                healthScore,
                amountDiff
        );
    }
}
