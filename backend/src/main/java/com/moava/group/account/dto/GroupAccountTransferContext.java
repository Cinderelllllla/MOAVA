package com.moava.group.account.dto;

public record GroupAccountTransferContext(
        Long currentAccountId,
        MockBalanceTransferPlan plan
) {
}
