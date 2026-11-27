package com.moava.group.account.dto;

// F19 - 서버 내부 계좌 소유 인증 대상
// Controller 응답으로 사용하지 않는다.
public record GroupAccountVerificationTarget(

        Long gyeAccountId,

        Long groupId,

        Long ownerId,

        String bankName,

        String accountNoEnc,

        String accountNoLast4,

        Integer keyVer,

        String verifyStatus,

        String accountStatus

) {

    // 암호문 및 계좌 정보 로그 노출 방지
    @Override
    public String toString() {
        return "GroupAccountVerificationTarget[REDACTED]";
    }
}
