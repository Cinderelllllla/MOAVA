package com.moava.finance.dto;

import java.math.BigDecimal;
import java.util.List;

/** F14 시뮬레이터 적금 금리 비교 응답 DTO */
public record SavingProductComparisonResponse(
        boolean available,
        Integer requestedTermMonths,
        Integer matchedTermMonths,
        int productCount,
        BigDecimal avgBasicRate,
        BigDecimal avgMaxRate,
        BigDecimal topMaxRate,
        String fetchedAt,
        List<Item> topProducts
) {
    public record Item(
            String bankName,
            String productName,
            int termMonths,
            BigDecimal rateBasic,
            BigDecimal rateMax
    ) {}

    public static SavingProductComparisonResponse unavailable(Integer requestedTermMonths) {
        return new SavingProductComparisonResponse(
                false,
                requestedTermMonths,
                null,
                0,
                null,
                null,
                null,
                null,
                List.of()
        );
    }
}
