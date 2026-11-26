package com.moava.group.account.service;

// F19 - 외부 계좌 소유 인증 연동 인터페이스
public interface AccountVerificationProvider {

    // 등록된 계좌의 실제 소유 여부 확인
    boolean verifyAccount(
            Long memberId,
            String bankName,
            String accountNumber
    );
}
