package com.moava.group.account.mapper;

import com.moava.group.account.dto.MockAccountLookupRow;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GroupAccountMockLinkMapper {

    // F20 - 모의 계좌 원장 정확 일치 조회
    // SELECT 전용: 잔액이나 계좌 상태를 변경하지 않는다.
    List<MockAccountLookupRow> findMockAccounts(
        @Param("bankCode") String bankCode,
        @Param("accountNumber") String accountNumber
    );
}
