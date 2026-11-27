package com.moava.group.account.mapper;

import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GroupAccountTransferExecutionMapper {

    // 기존 모의 계좌 잔액 차감
    int debit(
            @Param("fintechUseNum") String fintechUseNum,
            @Param("expectedBalance") BigDecimal expectedBalance,
            @Param("newBalance") BigDecimal newBalance
    );

    // 신규 모의 계좌 잔액 증가
    int credit(
            @Param("fintechUseNum") String fintechUseNum,
            @Param("expectedBalance") BigDecimal expectedBalance,
            @Param("newBalance") BigDecimal newBalance
    );

    // 모의 거래 원장 1건 기록
    int insertTransferRecord(
            @Param("bankTranId") String bankTranId,
            @Param("fintechUseNum") String fintechUseNum,
            @Param("inoutType") String inoutType,
            @Param("amount") BigDecimal amount,
            @Param("afterBalance") BigDecimal afterBalance,
            @Param("counterpartyFinNum") String counterpartyFinNum
    );

    // 기존 대표 계좌 종료
    int retireCurrentAccount(
            @Param("groupId") Long groupId,
            @Param("currentAccountId") Long currentAccountId
    );

    // 신규 대표 계좌 확정
    int activatePendingAccount(
            @Param("groupId") Long groupId,
            @Param("accountId") Long accountId
    );
}
