package com.moava.group.account.dto;

public record GroupAccountChangeVerifyResponse(
        Long groupId,
        Long gyeAccountId,
        String bankName,
        String accountMasked,
        String verifyStatus,
        String accountStatus,
        String message
) {
}
