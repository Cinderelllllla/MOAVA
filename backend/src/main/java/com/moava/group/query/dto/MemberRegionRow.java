package com.moava.group.query.dto;

/** F11 모임 추천 기준 회원 활동 지역 조회 DTO */
public record MemberRegionRow(
        String regionSido,
        String regionSigungu,
        String regionDong
) {}
