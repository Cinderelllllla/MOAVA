package com.moava.group.account.dto;

public record GroupAccountConsentTargetInfo(
        String bankName,
        String accountMasked,
        String myAgreedYn
) {
}
