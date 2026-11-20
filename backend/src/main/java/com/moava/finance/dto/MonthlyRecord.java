package com.moava.finance.dto;

/** 월별 수입·지출 한 달치. 금액은 원 단위(화면의 만 원 변환은 프론트에서). */
public record MonthlyRecord(String yearMonth, long income, long expense) {
    /** 남은 돈 = 수입 - 지출 (적자면 음수) */
    public long balance() {
        return income - expense;
    }
}
