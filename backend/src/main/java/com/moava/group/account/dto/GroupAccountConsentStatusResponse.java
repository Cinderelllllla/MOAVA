package com.moava.group.account.dto;

public record GroupAccountConsentStatusResponse(
        Long groupId,
        Long gyeAccountId,
        int totalMembers,
        int agreedMembers,
        int rejectedMembers,
        int pendingMembers,
        boolean allAgreed,
        String myAgreedYn,
        boolean canRespond,
        String bankName,
        String accountMasked
) {
}
