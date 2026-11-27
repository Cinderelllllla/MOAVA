package com.moava.group.account.dto;

// F20 - 회원 계좌 변경 동의 또는 거절
public record GroupAccountConsentRequest(
        Boolean agreed
) {
}
