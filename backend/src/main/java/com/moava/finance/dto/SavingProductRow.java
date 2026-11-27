package com.moava.finance.dto;

import java.math.BigDecimal;

/** F14 PRODUCTS_SAVING 적금 상품 금리 행 DTO */
public record SavingProductRow(
        Long productId,
        String finCoNo,
        String bankName,
        String productName,
        int termMonths,
        BigDecimal rateBasic,
        BigDecimal rateMax,
        String fetchedAt
) {
    /** 우대 금리가 없으면 기본 금리를 최고 금리로 사용한다. */
    public BigDecimal effectiveMaxRate() {
        return rateMax != null ? rateMax : rateBasic;
    }
}
