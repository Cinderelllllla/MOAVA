package com.moava.group.account.dto;

import java.time.LocalDateTime;

// F19 - 현재 모임 계좌 조회 응답
public record GroupAccountResponse(

        Long gyeAccountId,

        Long groupId,

        String bankName,

        String accountMasked,

        String accountLast4,

        String verifyStatus,

        String accountStatus,

        LocalDateTime verifiedAt

) {
}
