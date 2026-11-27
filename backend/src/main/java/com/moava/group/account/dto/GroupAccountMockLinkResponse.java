package com.moava.group.account.dto;

import java.math.BigDecimal;
import java.util.List;

public record GroupAccountMockLinkResponse(
        Long groupId,
        Long gyeAccountId,
        boolean currentLinked,
        boolean pendingLinked,
        BigDecimal currentBalance,
        BigDecimal pendingBalance,
        boolean transferPrerequisitesReady,
        List<String> blockers
) {
}
