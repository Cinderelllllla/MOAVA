package com.moava.finance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * F09 자가 응답 4문항 (맞춤 추천 1단계). 값은 화면 선택지 코드이고 ML 서버(ml/src/common/self_report.py)와 같다.
 * 민감한 신용 정보라 DB 에 저장하지 않고 로그에도 남기지 않는다 — ML 에 넘겨 등급만 받는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SelfReportRequest(
        @JsonProperty("late_count_12m") String lateCount12m,
        @JsonProperty("max_late_period") String maxLatePeriod,
        @JsonProperty("loan_count") String loanCount,
        @JsonProperty("card_usage") String cardUsage) {

    /** 최근 1년간 30일 이상 연체 횟수 */
    public static final Set<String> LATE_COUNT_12M = Set.of("0", "1", "2", "3_PLUS");
    /** 최근 6개월 가장 길게 연체한 기간 */
    public static final Set<String> MAX_LATE_PERIOD = Set.of("NONE", "UNDER_1M", "M1_2", "M2_3", "OVER_3M");
    /** 갚고 있는 대출 수 */
    public static final Set<String> LOAN_COUNT = Set.of("0", "1", "2", "3", "4", "5_PLUS");
    /** 카드 한도 중 쓰는 비율 */
    public static final Set<String> CARD_USAGE = Set.of("NO_CARD", "UNDER_30", "P30_50", "P50_70", "P70_90", "OVER_90");

    /** 4문항 모두 정해진 선택지 중 하나인지 */
    public boolean isValid() {
        return lateCount12m != null && LATE_COUNT_12M.contains(lateCount12m)
                && maxLatePeriod != null && MAX_LATE_PERIOD.contains(maxLatePeriod)
                && loanCount != null && LOAN_COUNT.contains(loanCount)
                && cardUsage != null && CARD_USAGE.contains(cardUsage);
    }

    /** ML 요청 본문의 self_report 객체 */
    public Map<String, Object> toMl() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("late_count_12m", lateCount12m);
        m.put("max_late_period", maxLatePeriod);
        m.put("loan_count", loanCount);
        m.put("card_usage", cardUsage);
        return m;
    }

    /** 값이 로그·예외 메시지에 찍히지 않도록 가린다 */
    @Override
    public String toString() {
        return "SelfReportRequest[****]";
    }
}
