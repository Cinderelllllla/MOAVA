package com.moava.group.account.dto;

import java.math.BigDecimal;

public record MockAccountLookupRow(
        String fintechUseNum,
        String bankCode,
        String bankName,
        String accountNo,
        BigDecimal balance,
        String status,
        String leaderYn
) {
    @Override
    public String toString() {
        return "MockAccountLookupRow[REDACTED]";
    }
}
