package com.moava.finance.service;

import com.moava.finance.dto.KosisHouseholdRow;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KOSIS '소득10분위별 가구당 가계수지' 응답 행들 -> KOSIS_HOUSEHOLD 10줄.
 *
 * 쓰는 값: 분위별 [소득, 소비지출, 비소비지출] 월평균(원).
 * KOSIS 는 분위의 경계값이 아니라 분위별 "평균 소득"만 주므로, 경계는 이렇게 정한다.
 *   - 1분위 하한 = 0
 *   - d분위 하한 = (d-1분위 평균소득 + d분위 평균소득) / 2  (이웃 분위 평균의 중간)
 *   - d분위 상한 = d+1분위 하한 - 1, 10분위 상한 = null
 * 여러 시점이 오면, 10개 분위 x 3개 항목이 모두 있는 가장 최근 시점을 쓴다.
 */
public final class KosisHouseholdAssembler {

    static final String INCOME = "소득";
    static final String CONSUMPTION = "소비지출";
    static final String NON_CONSUMPTION = "비소비지출";
    private static final List<String> ITEMS = List.of(INCOME, CONSUMPTION, NON_CONSUMPTION);
    private static final Pattern DECILE = Pattern.compile("^(\\d{1,2})분위$");

    private KosisHouseholdAssembler() {
    }

    public static List<KosisHouseholdRow> assemble(List<Map<String, String>> rows) {
        // 시점 -> 분위 -> 항목 -> 값(원)
        Map<String, Map<Integer, Map<String, Double>>> byPeriod = new TreeMap<>(Comparator.reverseOrder());
        TreeSet<String> seenNames = new TreeSet<>();
        for (Map<String, String> row : rows) {
            List<String> names = names(row);
            seenNames.addAll(names);
            Optional<Integer> decile = names.stream().map(KosisHouseholdAssembler::decileOf)
                    .filter(Optional::isPresent).map(Optional::get).findFirst();
            Optional<String> item = names.stream().filter(ITEMS::contains).findFirst();
            Double value = amount(row.get("DT"), row.get("UNIT_NM"));
            String period = row.get("PRD_DE");
            if (decile.isEmpty() || item.isEmpty() || value == null || period == null || period.isBlank()
                    || isPercent(row.get("UNIT_NM"))) {
                continue; // 전체평균·다른 항목·빈 값·비율(%) 은 건너뜀
            }
            String key = baseYm(period, row.get("PRD_SE"));
            Double old = byPeriod.computeIfAbsent(key, k -> new TreeMap<>())
                    .computeIfAbsent(decile.get(), k -> new TreeMap<>())
                    .put(item.get(), value);
            if (old != null && !old.equals(value)) {
                throw new IllegalStateException("같은 분위·항목·시점에 값이 여러 개입니다 (" + decile.get() + "분위 " + item.get()
                        + " " + key + "). URL 의 itmId 를 금액(명목) 항목 하나만 남기세요. 이번 행의 항목명: " + row.get("ITM_NM"));
            }
        }
        for (Map.Entry<String, Map<Integer, Map<String, Double>>> e : byPeriod.entrySet()) {
            if (isComplete(e.getValue())) {
                return build(e.getKey(), e.getValue());
            }
        }
        throw new IllegalStateException("KOSIS 응답에서 1~10분위의 소득·소비지출·비소비지출이 모두 있는 시점을 찾지 못했습니다. "
                + "통계표/항목 선택을 확인하세요. 응답에 있던 분류·항목 이름: " + limit(seenNames));
    }

    private static boolean isComplete(Map<Integer, Map<String, Double>> deciles) {
        for (int d = 1; d <= 10; d++) {
            Map<String, Double> items = deciles.get(d);
            if (items == null || !items.keySet().containsAll(ITEMS)) {
                return false;
            }
        }
        return true;
    }

    private static List<KosisHouseholdRow> build(String baseYm, Map<Integer, Map<String, Double>> deciles) {
        long[] avgIncome = new long[11];
        for (int d = 1; d <= 10; d++) {
            avgIncome[d] = Math.round(deciles.get(d).get(INCOME));
            if (d > 1 && avgIncome[d] <= avgIncome[d - 1]) {
                throw new IllegalStateException("분위 평균소득이 오름차순이 아닙니다 (" + (d - 1) + "분위 " + avgIncome[d - 1]
                        + " >= " + d + "분위 " + avgIncome[d] + ")");
            }
        }
        long[] low = new long[12];
        low[1] = 0;
        for (int d = 2; d <= 10; d++) {
            low[d] = Math.round((avgIncome[d - 1] + avgIncome[d]) / 2.0);
        }
        List<KosisHouseholdRow> result = new ArrayList<>();
        for (int d = 1; d <= 10; d++) {
            Long high = d == 10 ? null : low[d + 1] - 1;
            Map<String, Double> items = deciles.get(d);
            result.add(new KosisHouseholdRow(d, low[d], high,
                    Math.round(items.get(CONSUMPTION)), Math.round(items.get(NON_CONSUMPTION)), baseYm));
        }
        return result;
    }

    /** 분류값 이름(C1_NM~C8_NM)과 항목명(ITM_NM)을 공백 없이·전각 숫자를 반각으로 정리해서 모은다. */
    private static List<String> names(Map<String, String> row) {
        List<String> names = new ArrayList<>();
        for (String key : new String[]{"ITM_NM", "C1_NM", "C2_NM", "C3_NM", "C4_NM", "C5_NM", "C6_NM", "C7_NM", "C8_NM"}) {
            String v = row.get(key);
            if (v != null && !v.isBlank()) {
                names.add(normalize(v));
            }
        }
        return names;
    }

    static String normalize(String v) {
        return Normalizer.normalize(v, Normalizer.Form.NFKC).replaceAll("\\s+", "");
    }

    static Optional<Integer> decileOf(String name) {
        Matcher m = DECILE.matcher(name);
        if (!m.matches()) {
            return Optional.empty();
        }
        int d = Integer.parseInt(m.group(1));
        return d >= 1 && d <= 10 ? Optional.of(d) : Optional.empty();
    }

    /** 숫자가 아니면(통계부호 '-' 등) null. 단위가 천원·만원이면 원으로 바꾼다. */
    static Double amount(String dt, String unit) {
        if (dt == null) {
            return null;
        }
        String cleaned = dt.replace(",", "").strip();
        double v;
        try {
            v = Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
        String u = unit == null ? "" : normalize(unit);
        if (u.contains("천원")) {
            return v * 1_000;
        }
        if (u.contains("만원")) {
            return v * 10_000;
        }
        return v;
    }

    static boolean isPercent(String unit) {
        return unit != null && (unit.contains("%") || normalize(unit).contains("퍼센트"));
    }

    /**
     * PRD_DE -> YYYYMM (분기 마지막 달).
     * 분기(PRD_SE=Q/분기): YYYY0Q 또는 YYYYQ -> 분기 x 3 월. 연(YYYY) -> YYYY12. 월(YYYYMM) -> 그대로.
     */
    static String baseYm(String period, String prdSe) {
        String p = period.replaceAll("\\D", "");
        String se = prdSe == null ? "" : normalize(prdSe).toUpperCase();
        boolean quarterly = se.equals("Q") || se.contains("분기");
        if (quarterly && (p.length() == 5 || p.length() == 6)) {
            int q = p.charAt(p.length() - 1) - '0';
            if (q < 1 || q > 4) {
                throw new IllegalStateException("알 수 없는 분기 시점: " + period);
            }
            return p.substring(0, 4) + String.format("%02d", q * 3);
        }
        if (p.length() >= 6) {
            return p.substring(0, 6);
        }
        if (p.length() == 5) {
            int q = p.charAt(4) - '0';
            if (q < 1 || q > 4) {
                throw new IllegalStateException("알 수 없는 분기 시점: " + period);
            }
            return p.substring(0, 4) + String.format("%02d", q * 3);
        }
        if (p.length() == 4) {
            return p + "12";
        }
        throw new IllegalStateException("알 수 없는 수록시점 형식: " + period);
    }

    private static String limit(TreeSet<String> names) {
        return names.size() <= 30 ? names.toString() : new ArrayList<>(names).subList(0, 30) + " ...";
    }
}
