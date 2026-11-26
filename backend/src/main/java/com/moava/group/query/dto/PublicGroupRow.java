package com.moava.group.query.dto;

import java.math.BigDecimal;

/** MyBatis 조회용 공개 모임 내부 행 DTO (CLOB·CHAR(1) 안전 매핑 후 서비스에서 PublicGroupResponse로 변환) */
public record PublicGroupRow(
        Long groupId,
        String title,
        Long ownerId,
        String ownerName,
        String status,
        String purpose,
        String regionSido,
        String regionSigungu,
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
        String orderConfirmedYn,
        String role
) {}
