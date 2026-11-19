package com.moava.finance.service;

import com.moava.finance.dto.KosisHouseholdRow;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 월수입이 몇 분위인지 고르는 순수 계산 (DB·스프링 없이 테스트 가능).
 * 규칙: INCOME_LOW <= 월수입 인 분위 중 가장 높은 분위. 어느 분위에도 못 들면 가장 낮은 분위.
 */
public final class DecilePicker {
    private DecilePicker() {
    }

    public static Optional<KosisHouseholdRow> pick(List<KosisHouseholdRow> rows, long income) {
        if (rows == null || rows.isEmpty()) {
            return Optional.empty();
        }
        Optional<KosisHouseholdRow> hit = rows.stream()
                .filter(r -> r.incomeLow() <= income)
                .max(Comparator.comparingInt(KosisHouseholdRow::decile));
        if (hit.isPresent()) {
            return hit;
        }
        return rows.stream().min(Comparator.comparingInt(KosisHouseholdRow::decile));
    }
}
