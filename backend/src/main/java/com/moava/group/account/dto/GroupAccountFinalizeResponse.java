package com.moava.group.account.dto;

import java.math.BigDecimal;

public record GroupAccountFinalizeResponse(
        Long groupId,
        Long gyeAccountId,
        BigDecimal transferredAmount,
        String accountStatus,
        String message
) {
}
