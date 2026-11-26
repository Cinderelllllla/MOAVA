package com.moava.group.account.dto;

import java.time.LocalDateTime;

// F19 - 모임 계좌 인증 결과 응답
public record GroupAccountVerifyResponse(

        Long gyeAccountId,

        Long groupId,

        String bankName,

        String accountMasked,

        String verifyStatus,

        LocalDateTime verifiedAt,

        String message

) {
}
