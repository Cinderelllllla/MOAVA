package com.moava.member.dto;

public record MemberResponse(
        Long id,
        String name,
        String email,
        String phoneEnc,
        String regionSido,
        String regionSigungu,
        String regionDong,
        String status,
        String bannedYn,
        Integer keyVer,
        String signupType
) {
    public MemberResponse(
            Long id,
            String name,
            String email,
            String phoneEnc,
            String regionSido,
            String regionSigungu,
            String regionDong,
            String status,
            String bannedYn,
            Integer keyVer
    ) {
        this(id, name, email, phoneEnc, regionSido, regionSigungu, regionDong, status, bannedYn, keyVer, "EMAIL");
    }
}
