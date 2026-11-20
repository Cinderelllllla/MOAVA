package com.moava.finance.dto;

import java.util.List;

/** F10 월별 기록 목록. items 는 오래된 달 -> 최신 달 순서. */
public record MonthlyListResponse(List<Item> items) {
    public record Item(String yearMonth, long income, long expense, long balance) {
    }
}
