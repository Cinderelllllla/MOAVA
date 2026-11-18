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
        Long amount,
        Integer maxMembers,
        Integer months,
        Integer payDay,
        BigDecimal assumedRate,
        String visibility,
        String description
) {}