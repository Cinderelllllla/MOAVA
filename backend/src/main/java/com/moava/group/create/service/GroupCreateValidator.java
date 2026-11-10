
package com.moava.group.create.service;

import com.moava.group.create.dto.GroupCreateRequest;
import java.math.BigDecimal;
import java.util.Set;

public final class GroupCreateValidator {

    private static final Set<String> PURPOSES =
        Set.of("SAVINGS", "INVEST", "SOCIAL", "STARTUP", "TRAVEL");

    private static final Set<String> VISIBILITIES =
        Set.of("PUBLIC", "PRIVATE");

    private static final Set<String> OVERDUE_POLICIES =
        Set.of("ALERT_ONLY", "ALERT_AND_RESTRICT");

    private GroupCreateValidator() {
    }

    public static void validate(GroupCreateRequest request) {

        if (request == null) {
            throw new IllegalArgumentException("모임 정보를 입력해 주세요.");
        }

        if (blank(request.title()) || request.title().length() > 100) {
            throw new IllegalArgumentException("모임 이름을 확인해 주세요.");
        }

        if (!PURPOSES.contains(request.purpose())) {
            throw new IllegalArgumentException("모임 목적이 올바르지 않습니다.");
        }

       
        if (blank(request.regionSido())
                || request.regionSido().length() > 30
                || blank(request.regionSigungu())
                || request.regionSigungu().length() > 30) {
            throw new IllegalArgumentException(
                "시·도와 시·군·구를 정확히 입력해 주세요."
            );
        }


        if (request.amount() == null
                || request.amount() < 50000
                || request.amount() % 50000 != 0) {
            throw new IllegalArgumentException(
                "월 납입액은 5만 원 단위로 입력해 주세요."
            );
        }

        if (request.maxMembers() == null
                || request.maxMembers() < 2
                || request.maxMembers() > 30) {
            throw new IllegalArgumentException("인원은 2~30명이어야 합니다.");
        }

        if (request.months() == null
                || request.months() < 2
                || request.months() > 36) {
            throw new IllegalArgumentException("기간은 2~36개월이어야 합니다.");
        }

        if (request.payDay() == null
                || request.payDay() < 1
                || request.payDay() > 28) {
            throw new IllegalArgumentException("납입일은 1~28일이어야 합니다.");
        }

        BigDecimal rate = request.assumedRate();

        if (rate == null
                || rate.compareTo(BigDecimal.ZERO) < 0
                || rate.compareTo(new BigDecimal("20")) > 0
                || rate.scale() > 2) {
            throw new IllegalArgumentException("가정 연이율은 0~20%이며 소수 둘째 자리까지 가능합니다.");
        }

        if (!VISIBILITIES.contains(request.visibility())) {
            throw new IllegalArgumentException("공개 설정이 올바르지 않습니다.");
        }

        if (blank(request.rulesTitle()) || request.rulesTitle().length() > 100
                || blank(request.rulesBody())
                || blank(request.description()) || request.description().length() > 1000) {
            throw new IllegalArgumentException("모임 소개와 운영 규칙을 확인해 주세요.");
        }

        if (!OVERDUE_POLICIES.contains(request.overduePolicy())) {
            throw new IllegalArgumentException("미납 대응 규칙이 올바르지 않습니다.");
        }

        if (blank(request.bankName()) || request.bankName().length() > 50
                || request.accountNumber() == null
                || !request.accountNumber().matches("[0-9]{8,30}")) {
            throw new IllegalArgumentException("모임 계좌 정보를 확인해 주세요.");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
