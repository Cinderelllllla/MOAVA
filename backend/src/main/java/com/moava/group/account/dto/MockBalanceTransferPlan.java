package com.moava.group.account.dto;

import java.math.BigDecimal;

// F20 내부 전용: API 응답으로 직접 반환하지 않는다.
public record MockBalanceTransferPlan(
        String sourceFintechUseNum,
        String targetFintechUseNum,
        BigDecimal transferAmount,
        BigDecimal sourceBalanceAfter,
        BigDecimal targetBalanceAfter,
        boolean transferRequired
) {
    @Override
    public String toString() {
        return "MockBalanceTransferPlan[REDACTED]";
    }
}
