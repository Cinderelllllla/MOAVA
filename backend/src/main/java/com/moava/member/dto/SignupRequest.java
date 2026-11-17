package com.moava.member.dto;

public record SignupRequest(String name, String email, String password, String phone, String regionSido, String regionSigungu, Boolean termsAgreed, Boolean privacyAgreed) {}
