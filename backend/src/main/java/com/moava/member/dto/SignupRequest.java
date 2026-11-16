package com.moava.member.dto;

public record SignupRequest(String name, String email, String password, String phone, String regionSido, String regionSigungu, boolean termsAgreed, boolean privacyAgreed) {}
