package com.moava.group.query.dto;

import java.math.BigDecimal;

public record PublicGroupResponse(
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
        String role
) {}
