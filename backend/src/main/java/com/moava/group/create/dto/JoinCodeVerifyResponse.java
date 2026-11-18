package com.moava.group.create.dto;

public record JoinCodeVerifyResponse(
        boolean verified,
        String status,
        String message,
        int remainingAttempts
) {
}
