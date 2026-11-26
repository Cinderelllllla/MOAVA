package com.moava.group.search.dto;

import java.math.BigDecimal;

/** MyBatis 조회용 ES 색인 원본 행 DTO */
public record GroupSearchIndexRow(
        Long groupId,
        String title,
        Long ownerId,
        String status,
        String purpose,
        String visibility,
        String regionSido,
        String regionSigungu,
        String regionDong,
        Long amount,
        Integer maxMembers,
        Integer currentMembers,
        BigDecimal lat,
        BigDecimal lng,
        String description
) {}
