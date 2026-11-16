package com.moava.member.dto;

public record AuthResponse(String accesToken, String tokenType, MemberView user) {}
