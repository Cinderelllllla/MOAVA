package com.moava.finance.service;

import com.moava.finance.dto.SavingProductComparisonResponse;
import com.moava.finance.dto.SavingProductRow;
import com.moava.finance.mapper.SavingProductMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * F14 계모임 시뮬레이터 — 시중 적금 금리 비교 서비스 ({@code PRODUCTS_SAVING}).
 * <ul>
 *   <li>요청한 저축 기간(개월)과 일치하는 상품이 있으면 해당 만기 상품을 비교한다.</li>
 *   <li>일치하는 만기가 없으면 {@code PRODUCTS_SAVING}에 존재하는 가장 가까운 만기 기간을 선택한다.</li>
 *   <li>테이블이 비어 있으면 {@code available=false} 를 반환해 시뮬레이터 본 기능은 정상 동작한다.</li>
 * </ul>
 */
@Service
public class SavingProductService {

    private static final int TOP_LIMIT = 5;

    private final SavingProductMapper mapper;

    public SavingProductService(SavingProductMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public SavingProductComparisonResponse compare(Integer termMonths) {
        if (termMonths != null && (termMonths < 1 || termMonths > 120)) {
            throw new FinanceException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_TERM",
                    "저축 기간은 1~120개월 사이여야 합니다."
            );
        }

        List<SavingProductRow> allRows = mapper.selectAll();
        if (allRows == null || allRows.isEmpty()) {
            return SavingProductComparisonResponse.unavailable(termMonths);
        }

        Integer matchedTerm = pickMatchedTerm(allRows, termMonths);
        List<SavingProductRow> targetRows = matchedTerm == null
                ? allRows
                : allRows.stream()
                        .filter(row -> row.termMonths() == matchedTerm)
                        .toList();

        if (targetRows.isEmpty()) {
            return SavingProductComparisonResponse.unavailable(termMonths);
        }

        List<SavingProductRow> sorted = targetRows.stream()
                .sorted(Comparator
                        .comparing(SavingProductRow::effectiveMaxRate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SavingProductRow::rateBasic, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SavingProductRow::productId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        BigDecimal sumBasic = BigDecimal.ZERO;
        BigDecimal sumMax = BigDecimal.ZERO;
        BigDecimal topMax = null;
        String latestFetchedAt = null;

        for (SavingProductRow row : sorted) {
            BigDecimal basic = row.rateBasic() != null ? row.rateBasic() : BigDecimal.ZERO;
            BigDecimal max = row.effectiveMaxRate() != null ? row.effectiveMaxRate() : basic;
            sumBasic = sumBasic.add(basic);
            sumMax = sumMax.add(max);
            if (topMax == null || max.compareTo(topMax) > 0) {
                topMax = max;
            }
            if (row.fetchedAt() != null && (latestFetchedAt == null || row.fetchedAt().compareTo(latestFetchedAt) > 0)) {
                latestFetchedAt = row.fetchedAt();
            }
        }

        BigDecimal count = BigDecimal.valueOf(sorted.size());
        BigDecimal avgBasic = sumBasic.divide(count, 2, RoundingMode.HALF_UP);
        BigDecimal avgMax = sumMax.divide(count, 2, RoundingMode.HALF_UP);
        BigDecimal topMaxScaled = topMax == null ? null : topMax.setScale(2, RoundingMode.HALF_UP);

        List<SavingProductComparisonResponse.Item> topProducts = sorted.stream()
                .limit(TOP_LIMIT)
                .map(row -> new SavingProductComparisonResponse.Item(
                        row.bankName(),
                        row.productName(),
                        row.termMonths(),
                        scale2(row.rateBasic()),
                        scale2(row.effectiveMaxRate())
                ))
                .toList();

        return new SavingProductComparisonResponse(
                true,
                termMonths,
                matchedTerm,
                sorted.size(),
                avgBasic,
                avgMax,
                topMaxScaled,
                latestFetchedAt,
                topProducts
        );
    }

    private static Integer pickMatchedTerm(List<SavingProductRow> rows, Integer requestedTerm) {
        if (requestedTerm == null) {
            return null;
        }
        return rows.stream()
                .map(SavingProductRow::termMonths)
                .distinct()
                .min(Comparator
                        .comparingInt((Integer t) -> Math.abs(t - requestedTerm))
                        .thenComparingInt(Integer::intValue))
                .orElse(null);
    }

    private static BigDecimal scale2(BigDecimal value) {
        return Objects.isNull(value) ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
