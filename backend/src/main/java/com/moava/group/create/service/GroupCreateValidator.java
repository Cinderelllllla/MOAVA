
package com.moava.group.create.service;

import com.moava.group.create.dto.GroupCreateRequest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public final class GroupCreateValidator {

    private static final Set<String> PURPOSES =
            Set.of("SAVINGS", "INVEST", "SOCIAL", "STARTUP", "TRAVEL");

    private static final Set<String> VISIBILITIES =
            Set.of("PUBLIC", "PRIVATE");

    private static final Set<String> OVERDUE_POLICIES =
            Set.of("ALERT_ONLY", "ALERT_RESTRICT");

    private GroupCreateValidator() {
    }

    public static void validate(GroupCreateRequest request) {

        // 1. 요청 데이터 확인
        if (request == null) {
            throw new IllegalArgumentException(
                    "모임 정보를 입력해 주세요."
            );
        }

        // 2. 모임 이름 (GYE_GROUP.TITLE: 100 BYTE)
        if (blank(request.title())
                || exceedsBytes(request.title(), 100)) {
            throw new IllegalArgumentException(
                    "모임 이름을 확인해 주세요."
            );
        }

        // 3. 모임 목적
        if (request.purpose() == null
                || !PURPOSES.contains(request.purpose())) {
            throw new IllegalArgumentException(
                    "모임 목적이 올바르지 않습니다."
            );
        }

        // 4. 지역 (각각 30 BYTE)
        if (blank(request.regionSido())
                || exceedsBytes(request.regionSido(), 30)
                || blank(request.regionSigungu())
                || exceedsBytes(request.regionSigungu(), 30)) {
            throw new IllegalArgumentException(
                    "시·도와 시·군·구를 정확히 입력해 주세요."
            );
        }

        // 5. 월 납입액 (NUMBER(12), 5만 원 단위)
        if (request.amount() == null
                || request.amount() < 50000
                || request.amount() > 999999999999L
                || request.amount() % 50000 != 0) {
            throw new IllegalArgumentException(
                    "월 납입액은 5만 원 단위로 입력해 주세요."
            );
        }

        // 6. 최대 인원 (2~30명)
        if (request.maxMembers() == null
                || request.maxMembers() < 2
                || request.maxMembers() > 30) {
            throw new IllegalArgumentException(
                    "인원은 2~30명이어야 합니다."
            );
        }

        // 7. 모임 기간 (2~36개월)
        if (request.months() == null
                || request.months() < 2
                || request.months() > 36) {
            throw new IllegalArgumentException(
                    "기간은 2~36개월이어야 합니다."
            );
        }

        // 8. 납입일 (1~28일)
        if (request.payDay() == null
                || request.payDay() < 1
                || request.payDay() > 28) {
            throw new IllegalArgumentException(
                    "납입일은 1~28일이어야 합니다."
            );
        }

        // 9. 가정 연이율 (NUMBER(4,2), 0~20%)
        BigDecimal rate = request.assumedRate();

        if (rate == null
                || rate.compareTo(BigDecimal.ZERO) < 0
                || rate.compareTo(new BigDecimal("20")) > 0
                || rate.scale() > 2) {
            throw new IllegalArgumentException(
                    "가정 연이율은 0~20%이며 소수 둘째 자리까지 가능합니다."
            );
        }

        // 10. 공개 여부
        if (request.visibility() == null
                || !VISIBILITIES.contains(request.visibility())) {
            throw new IllegalArgumentException(
                    "공개 설정이 올바르지 않습니다."
            );
        }

        // 11. 모임 소개 및 운영 규칙
        // RULES_TITLE: 100 BYTE
        // DESCRIPTION: 1000 BYTE
        // RULES_BODY: CLOB
        if (blank(request.rulesTitle())
                || exceedsBytes(request.rulesTitle(), 100)
                || blank(request.rulesBody())
                || blank(request.description())
                || exceedsBytes(request.description(), 1000)) {
            throw new IllegalArgumentException(
                    "모임 소개와 운영 규칙을 확인해 주세요."
            );
        }

        // 12. 미납 대응 정책
        if (request.overduePolicy() == null
                || !OVERDUE_POLICIES.contains(request.overduePolicy())) {
            throw new IllegalArgumentException(
                    "미납 대응 규칙이 올바르지 않습니다."
            );
        }

        // 13. 모임 계좌
        // BANK_NAME: 50 BYTE
        // 계좌번호: 숫자 8~30자리
        if (blank(request.bankName())
                || exceedsBytes(request.bankName(), 50)
                || request.accountNumber() == null
                || !request.accountNumber().matches("[0-9]{8,30}")) {
            throw new IllegalArgumentException(
                    "모임 계좌 정보를 확인해 주세요."
            );
        }
    }

    // null 또는 공백 문자열 확인
    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    // Oracle AL32UTF8의 BYTE 길이 제한 검사
    private static boolean exceedsBytes(String value, int maxBytes) {
        return value != null
                && value.getBytes(StandardCharsets.UTF_8).length > maxBytes;
    }
}
