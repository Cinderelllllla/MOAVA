package com.moava.finance.dto;

/** KOSIS_HOUSEHOLD 한 줄 (소득 분위별 평균 지출, 원 단위). incomeHigh 가 null 이면 최고 분위. */
public record KosisHouseholdRow(int decile, long incomeLow, Long incomeHigh,
                                long avgConsumption, long avgNonconsumption, String baseYm) {
}
