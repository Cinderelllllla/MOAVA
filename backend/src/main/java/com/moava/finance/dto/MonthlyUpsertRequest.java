package com.moava.finance.dto;

/** F10 월별 기록 저장 요청. yearMonth 는 YYYYMM, 금액은 원 단위. */
public record MonthlyUpsertRequest(String yearMonth, Long income, Long expense) {
}
