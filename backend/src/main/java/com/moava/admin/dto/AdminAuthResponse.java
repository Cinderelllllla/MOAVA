package com.moava.admin.dto;

public record AdminAuthResponse(
        String accessToken,
        String tokenType,
        AdminView user) {
}
