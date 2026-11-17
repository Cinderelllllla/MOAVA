package com.moava.member.dto;

/**프로트엔드에 전달하는 회원정보 DTO */
public record MemberView(String name, String email, String phone, String regionSido, String regionSigungu, String status) {} 