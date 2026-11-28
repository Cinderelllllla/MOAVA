package com.moava.member.dto;

/** 프론트엔드에 전달하는 회원정보 DTO */
public record MemberView(
        String name,
        String email,
        String phone,
        String regionSido,
        String regionSigungu,
        String regionDong,
        String status,
        String signupType
) {
    public MemberView(
            String name,
            String email,
            String phone,
            String regionSido,
            String regionSigungu,
            String regionDong,
            String status
    ) {
        this(name, email, phone, regionSido, regionSigungu, regionDong, status, "EMAIL");
    }
}