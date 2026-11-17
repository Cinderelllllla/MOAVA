package com.moava.member.dto;

public record AuthResponse(String accessToken, String tokenType, MemberView user) {}
