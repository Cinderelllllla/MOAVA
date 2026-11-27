package com.moava.group.account.service;

import com.moava.group.account.dto.MockAccountLookupRow;
import com.moava.group.account.dto.MockBalanceTransferPlan;

import java.math.BigDecimal;
import java.util.Objects;

import org.springframework.stereotype.Component;

@Component
public class MockBalanceTransferPlanner {

    // MOCK_KFTC_ACCOUNT.BALANCE = NUMBER(15)
    private static final BigDecimal MAX_BALANCE =
            new BigDecimal("999999999999999");

    // DB 접근 없이 이체 계획만 계산한다.
    public MockBalanceTransferPlan plan(
            MockAccountLookupRow source,
            MockAccountLookupRow target
    ) {
        validateAccount(source, "기존");
        validateAccount(target, "신규");

        if (Objects.equals(
                source.fintechUseNum(),
                target.fintechUseNum())) {
            throw new IllegalStateException(
                    "같은 모의 계좌로 잔액을 이전할 수 없습니다."
            );
        }

        // 전액 이전 정책
        BigDecimal amount = source.balance();

        BigDecimal sourceAfter =
                source.balance().subtract(amount);

        BigDecimal targetAfter =
                target.balance().add(amount);

        if (targetAfter.compareTo(MAX_BALANCE) > 0) {
            throw new IllegalStateException(
                    "신규 모의 계좌의 잔액 한도를 초과합니다."
            );
        }

        return new MockBalanceTransferPlan(
                source.fintechUseNum(),
                target.fintechUseNum(),
                amount,
                sourceAfter,
                targetAfter,
                amount.compareTo(BigDecimal.ZERO) > 0
        );
    }

    private void validateAccount(
            MockAccountLookupRow account,
            String label
    ) {
        if (account == null) {
            throw new IllegalStateException(
                    label + " 모의 계좌가 없습니다."
            );
        }

        if (account.fintechUseNum() == null
        || account.fintechUseNum().isBlank()
        || account.fintechUseNum().length() > 24) {
            throw new IllegalStateException(
                    label + " 모의 계좌 식별번호가 올바르지 않습니다."
            );
        }

        if (!"ACTIVE".equals(account.status())) {
            throw new IllegalStateException(
                    label + " 모의 계좌가 활성 상태가 아닙니다."
            );
        }

        if (!"Y".equals(account.leaderYn())) {
            throw new IllegalStateException(
                    label + " 모의 계좌는 모임장 수취 계좌가 아닙니다."
            );
        }

        BigDecimal balance = account.balance();

        if (balance == null
                || balance.scale() > 0
                    && balance.stripTrailingZeros().scale() > 0
                || balance.compareTo(BigDecimal.ZERO) < 0
                || balance.compareTo(MAX_BALANCE) > 0) {
            throw new IllegalStateException(
                    label + " 모의 계좌 잔액이 올바르지 않습니다."
            );
        }
    }
}
