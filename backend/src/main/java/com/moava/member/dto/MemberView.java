package com.moava.member.dto;

/**프로트엔드에 전달하는 회원정보 DTO */
public record MemberView(Long id, String name, String email, String phoneEnc, String regionSido, String regionSigungu, String status) {} 