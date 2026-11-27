package com.moava.group.account.service;

import com.moava.group.account.dto.MockAccountLookupRow;
import com.moava.group.account.dto.MockBalanceTransferPlan;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MockBalanceTransferPlannerTest {

    private final MockBalanceTransferPlanner planner =
            new MockBalanceTransferPlanner();

    private MockAccountLookupRow account(
            String fintechNum,
            String balance,
            String status,
            String leaderYn
    ) {
        return new MockAccountLookupRow(
                fintechNum,
                "004",
                "테스트은행",
                "123456789012",
                new BigDecimal(balance),
                status,
                leaderYn
        );
    }

    @Test
    void 기존잔액_전액이전_계획성공() {
        var source = account(
                "111111111111111111111111",
                "500000", "ACTIVE", "Y"
        );

        var target = account(
                "222222222222222222222222",
                "0", "ACTIVE", "Y"
        );

        MockBalanceTransferPlan result =
                planner.plan(source, target);

        assertEquals(
                0,
                result.transferAmount()
                        .compareTo(new BigDecimal("500000"))
        );
        assertEquals(
                0,
                result.sourceBalanceAfter()
                        .compareTo(BigDecimal.ZERO)
        );
        assertEquals(
                0,
                result.targetBalanceAfter()
                        .compareTo(new BigDecimal("500000"))
        );
        assertTrue(result.transferRequired());
    }

    @Test
    void 기존잔액이_0원이면_이체불필요() {
        var source = account(
                "111111111111111111111111",
                "0", "ACTIVE", "Y"
        );

        var target = account(
                "222222222222222222222222",
                "1000", "ACTIVE", "Y"
        );

        var result = planner.plan(source, target);

        assertFalse(result.transferRequired());
        assertEquals(
                0,
                result.targetBalanceAfter()
                        .compareTo(new BigDecimal("1000"))
        );
    }

    @Test
    void 동일계좌_이체불가() {
        var source = account(
                "111111111111111111111111",
                "500000", "ACTIVE", "Y"
        );

        var target = account(
                "111111111111111111111111",
                "0", "ACTIVE", "Y"
        );

        assertThrows(IllegalStateException.class,
                () -> planner.plan(source, target));
    }

    @Test
    void 비활성계좌_이체불가() {
        var source = account(
                "111111111111111111111111",
                "500000", "FROZEN", "Y"
        );

        var target = account(
                "222222222222222222222222",
                "0", "ACTIVE", "Y"
        );

        assertThrows(IllegalStateException.class,
                () -> planner.plan(source, target));
    }

    @Test
    void 신규계좌_잔액한도초과_이체불가() {
        var source = account(
                "111111111111111111111111",
                "100", "ACTIVE", "Y"
        );

        var target = account(
                "222222222222222222222222",
                "999999999999950", "ACTIVE", "Y"
        );

        assertThrows(IllegalStateException.class,
                () -> planner.plan(source, target));
    }
}
