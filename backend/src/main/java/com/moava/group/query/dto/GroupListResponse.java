package com.moava.group.query.dto;

import java.math.BigDecimal;

public record GroupListResponse(
        Long groupId,
        String title,
        Long ownerId,
        String status,
        String purpose,
        String regionSido,
        String regionSigungu,
        String regionDong,
        Long amount,
        Integer maxMembers,
        Integer currentMembers,
        Integer months,
        Integer payDay,
        BigDecimal assumedRate,
        String visibility,
        String description,
        String role
) {}
