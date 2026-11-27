package com.moava.group.account.dto;

// F20 - 계좌 변경 신청 결과
public record GroupAccountChangeResponse(
        Long gyeAccountId,
        Long groupId,
        String bankName,
        String accountMasked,
        String verifyStatus,
        String accountStatus,
        String message
) {
}
