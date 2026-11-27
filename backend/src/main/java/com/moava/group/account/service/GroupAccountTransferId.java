package com.moava.group.account.service;

public final class GroupAccountTransferId {

    private GroupAccountTransferId() {
    }

    // 10자리 기관 식별 + U + 9자리 신청 식별
    private static final String PREFIX = "MOAVAF2000U";

    public static String fromAccountId(Long accountId) {
        if (accountId == null || accountId <= 0) {
            throw new IllegalArgumentException(
                    "계좌 변경 신청 ID가 올바르지 않습니다."
            );
        }

        String suffix =
                Long.toString(accountId, 36).toUpperCase();

        if (suffix.length() > 9) {
            throw new IllegalStateException(
                    "이체 식별번호 생성 범위를 초과했습니다."
            );
        }

        return PREFIX
                + "0".repeat(9 - suffix.length())
                + suffix;
    }
}
