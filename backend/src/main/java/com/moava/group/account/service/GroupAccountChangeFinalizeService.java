package com.moava.group.account.service;

import com.moava.group.account.dto.GroupAccountFinalizeResponse;
import com.moava.group.account.dto.MockBalanceTransferPlan;
import com.moava.group.account.dto.GroupAccountTransferContext;
import com.moava.group.account.mapper.GroupAccountChangeMapper;
import com.moava.group.account.mapper.GroupAccountTransferExecutionMapper;

import java.math.BigDecimal;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupAccountChangeFinalizeService {

    private final GroupAccountTransferPreflightService preflight;
    private final GroupAccountTransferExecutionMapper executionMapper;
    private final GroupAccountChangeMapper changeMapper;

    public GroupAccountChangeFinalizeService(
            GroupAccountTransferPreflightService preflight,
            GroupAccountTransferExecutionMapper executionMapper,
            GroupAccountChangeMapper changeMapper
    ) {
        this.preflight = preflight;
        this.executionMapper = executionMapper;
        this.changeMapper = changeMapper;
    }

    // 내부 전용: Controller에는 아직 연결하지 않는다.
    @Transactional(rollbackFor = Exception.class)
    public GroupAccountFinalizeResponse finalizeChange(
            Long groupId,
            Long accountId,
            Long memberId
    ) {
        // 1. 현재 트랜잭션에서 검증 및 행 잠금
    GroupAccountTransferContext context =
            preflight.prepare(groupId, accountId, memberId);

    if (context == null
            || context.currentAccountId() == null
            || context.currentAccountId() <= 0) {
        throw new IllegalStateException(
                "기존 대표 계좌 ID가 올바르지 않습니다."
        );
    }

    MockBalanceTransferPlan plan = context.plan();

        if (plan == null
                || plan.sourceFintechUseNum() == null
                || plan.targetFintechUseNum() == null
                || Objects.equals(
                        plan.sourceFintechUseNum(),
                        plan.targetFintechUseNum())
                || plan.transferAmount() == null
                || plan.transferAmount().signum() < 0
                || plan.sourceBalanceAfter() == null
                || plan.targetBalanceAfter() == null
                || plan.sourceBalanceAfter().signum() != 0
                || plan.transferRequired()
                    != (plan.transferAmount().signum() > 0)) {
            throw new IllegalStateException(
                    "모의 잔액 이전 계획이 올바르지 않습니다."
            );
        }

        BigDecimal amount = plan.transferAmount();

        // 2. 양쪽 계좌의 거래 기록에 사용할 동일한 ID
        String bankTranId =
                GroupAccountTransferId.fromAccountId(accountId);

        // 3. 실제 이전할 금액이 있을 때만 출금·입금
        if (amount.signum() > 0) {

            BigDecimal sourceBefore = amount;

            BigDecimal targetBefore =
                    plan.targetBalanceAfter().subtract(amount);

            if (targetBefore.signum() < 0) {
                throw new IllegalStateException(
                        "신규 계좌 잔액 계산이 올바르지 않습니다."
                );
            }

            // 기존 계좌 출금
            requireOne(
                    executionMapper.debit(
                            plan.sourceFintechUseNum(),
                            sourceBefore,
                            plan.sourceBalanceAfter()
                    ),
                    "기존 모의 계좌 출금 실패"
            );

            // 신규 계좌 입금
            requireOne(
                    executionMapper.credit(
                            plan.targetFintechUseNum(),
                            targetBefore,
                            plan.targetBalanceAfter()
                    ),
                    "신규 모의 계좌 입금 실패"
            );

            // 출금 원장
            requireOne(
                    executionMapper.insertTransferRecord(
                            bankTranId,
                            plan.sourceFintechUseNum(),
                            "출금",
                            amount,
                            plan.sourceBalanceAfter(),
                            plan.targetFintechUseNum()
                    ),
                    "출금 원장 기록 실패"
            );

            // 입금 원장
            requireOne(
                    executionMapper.insertTransferRecord(
                            bankTranId,
                            plan.targetFintechUseNum(),
                            "입금",
                            amount,
                            plan.targetBalanceAfter(),
                            plan.sourceFintechUseNum()
                    ),
                    "입금 원장 기록 실패"
            );
        }

        // 4. 검증했던 기존 대표 계좌만 종료
        requireOne(
                executionMapper.retireCurrentAccount(
                        groupId,
                        context.currentAccountId()
                ),
                "기존 대표 계좌 종료 실패"
        );

        // 5. 신규 대표 계좌 확정
        requireOne(
                executionMapper.activatePendingAccount(
                        groupId, accountId
                ),
                "신규 대표 계좌 확정 실패"
        );

        // 6. 감사 로그
        requireOne(
                changeMapper.insertChangeAudit(
                        groupId,
                        memberId,
                        "모임 계좌 변경 확정"
                ),
                "계좌 변경 확정 감사 로그 실패"
        );

        return new GroupAccountFinalizeResponse(
                groupId,
                accountId,
                amount,
                "CURRENT",
                "모의 잔액 이전 및 계좌 변경이 완료되었습니다."
        );
    }

    private void requireOne(int updated, String message) {
        if (updated != 1) {
            throw new IllegalStateException(message);
        }
    }
}
