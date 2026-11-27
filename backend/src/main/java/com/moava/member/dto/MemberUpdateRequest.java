package com.moava.member.dto;

public record MemberUpdateRequest(String name, String email, String phone, String regionSido, String regionSigungu, String regionDong) {
}