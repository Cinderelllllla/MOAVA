package com.moava.group.account.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GroupAccountBankCodeResolver {

    private final Map<String, String> codes;

    public GroupAccountBankCodeResolver(
            @Value("${moava.f20.bank-code-map:}")
            String configuration
    ) {
        Map<String, String> parsed = new HashMap<>();

        if (configuration != null
                && !configuration.isBlank()) {

            for (String entry : configuration.split(",")) {

                String[] pair = entry.trim().split("=", -1);

                if (pair.length != 2) {
                    throw new IllegalArgumentException(
                            "F20 은행코드 설정 형식이 잘못되었습니다."
                    );
                }

                String bankName = pair[0].trim();
                String bankCode = pair[1].trim();

                if (bankName.isBlank()
                        || !bankCode.matches("[0-9]{3}")) {
                    throw new IllegalArgumentException(
                            "F20 은행코드 설정값이 올바르지 않습니다."
                    );
                }

                if (parsed.putIfAbsent(
                        bankName, bankCode) != null) {
                    throw new IllegalArgumentException(
                            "중복된 F20 은행명 설정입니다."
                    );
                }
            }
        }

        this.codes = Map.copyOf(parsed);
    }

    public String resolve(String bankName) {

        if (bankName == null || bankName.isBlank()) {
            throw new IllegalStateException(
                    "은행명이 등록되지 않았습니다."
            );
        }

        String bankCode = codes.get(bankName.trim());

        if (bankCode == null) {
            throw new IllegalStateException(
                    "모의 은행코드가 확인되지 않아 계좌 변경을 진행할 수 없습니다."
            );
        }

        return bankCode;
    }
}
