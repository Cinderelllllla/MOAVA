package com.moava.group.account.dto;

import java.util.List;

public record GroupAccountChangeReadinessResponse(
        Long groupId,
        Long gyeAccountId,
        int totalMembers,
        int agreedMembers,
        int rejectedMembers,
        int pendingMembers,
        boolean consentReady,
        boolean ownershipVerified,
        boolean mockTransferReady,
        boolean finalizeReady,
        List<String> blockers
) {
}
