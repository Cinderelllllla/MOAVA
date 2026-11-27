package com.moava.group.account.dto;

// F20 - 새 모임 계좌 변경 신청
public record GroupAccountChangeRequest(
        String bankName,
        String accountNumber
) {
}
