package com.moava.group.account.dto;

// F20 - 계좌 변경 신청 종료 응답
public record GroupAccountChangeCloseResponse(
        Long groupId,
        Long gyeAccountId,
        String requestStatus,
        String message
) {
}
