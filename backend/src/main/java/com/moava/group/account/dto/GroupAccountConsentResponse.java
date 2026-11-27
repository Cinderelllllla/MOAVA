package com.moava.group.account.dto;

// F20 - 회원 동의·거절 처리 결과
public record GroupAccountConsentResponse(
        Long groupId,
        Long gyeAccountId,
        Long memberId,
        String consentStatus,
        String message
) {
}
