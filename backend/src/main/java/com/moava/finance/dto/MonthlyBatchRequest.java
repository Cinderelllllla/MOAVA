package com.moava.finance.dto;

import java.util.List;

/** F10 여러 달 한 번에 저장 요청 (MOA-102). items 1~12개, 같은 연월은 한 번만. 금액은 원 단위. */
public record MonthlyBatchRequest(List<MonthlyUpsertRequest> items) {
}
