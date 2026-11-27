package com.moava.group.account.mapper;

import com.moava.group.account.dto.MockAccountLookupRow;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GroupAccountTransferLockMapper {

    // 동일 DB 트랜잭션에서 FOR UPDATE로 계좌 행 잠금
    MockAccountLookupRow lockMockAccount(
            @Param("fintechUseNum") String fintechUseNum
    );

    // 이 변경 신청의 거래 ID가 원장에 이미 사용되었는지 확인
    int countTransferRecords(
            @Param("bankTranId") String bankTranId
    );
}
