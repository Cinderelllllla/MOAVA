package com.moava.finance.dto;

/**
 * F07 지출 기본값 제안. 월수입이 속한 소득 분위의 평균 소비/비소비지출(원).
 * KOSIS 데이터가 비어 있으면 이 응답 대신 available=false 인 빈 응답을 돌려준다.
 */
public record SpendingDefaultResponse(boolean available, Integer decile,
                                      Long avgConsumption, Long avgNonconsumption, String baseYm) {
    public static SpendingDefaultResponse unavailable() {
        return new SpendingDefaultResponse(false, null, null, null, null);
    }
}
