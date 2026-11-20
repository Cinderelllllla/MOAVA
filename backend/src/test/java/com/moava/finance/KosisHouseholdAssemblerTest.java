package com.moava.finance;

import com.moava.finance.dto.KosisHouseholdRow;
import com.moava.finance.service.KosisHouseholdAssembler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KosisHouseholdAssemblerTest {

    // 테스트용 가짜 숫자 (실제 KOSIS 값 아님): d분위 평균소득 = d x 100만 원
    private static List<Map<String, String>> sample(String period, String unit, long scale, boolean fullWidth) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (int d = 1; d <= 10; d++) {
            String decile = (fullWidth ? toFullWidth(String.valueOf(d)) : String.valueOf(d)) + "분위";
            rows.add(row(decile, "소득", d * 1_000_000L / scale, unit, period));
            rows.add(row(decile, "소비지출", d * 200_000L / scale, unit, period));
            rows.add(row(decile, "비소비지출", d * 50_000L / scale, unit, period));
            rows.add(row(decile, "가계지출", 1, unit, period)); // 안 쓰는 항목
        }
        rows.add(row("전체평균", "소득", 999, unit, period)); // 안 쓰는 분류
        return rows;
    }

    private static Map<String, String> row(String c1, String item, long value, String unit, String period) {
        Map<String, String> m = new HashMap<>();
        m.put("C1_NM", c1);
        m.put("ITM_NM", item);
        m.put("DT", String.valueOf(value));
        m.put("UNIT_NM", unit);
        m.put("PRD_DE", period);
        if (period.length() == 6) m.put("PRD_SE", "Q"); // KOSIS 분기 형식 YYYY0Q
        return m;
    }

    private static String toFullWidth(String digits) {
        StringBuilder sb = new StringBuilder();
        for (char c : digits.toCharArray()) sb.append((char) (c - '0' + '０'));
        return sb.toString();
    }

    @Test
    void 열개_분위를_만들고_경계는_이웃_평균의_중간() {
        List<KosisHouseholdRow> r = KosisHouseholdAssembler.assemble(sample("202402", "원", 1, false));
        assertEquals(10, r.size());
        assertEquals(0, r.get(0).incomeLow());
        assertEquals(Long.valueOf(1_499_999L), r.get(0).incomeHigh());
        assertEquals(1_500_000, r.get(1).incomeLow());      // (100만 + 200만) / 2
        assertEquals(9_500_000, r.get(9).incomeLow());
        assertNull(r.get(9).incomeHigh());
        assertEquals(600_000, r.get(2).avgConsumption());
        assertEquals(150_000, r.get(2).avgNonconsumption());
        assertEquals("202406", r.get(0).baseYm());          // 2024년 2분기 -> 6월
    }

    @Test
    void 천원_단위와_전각_숫자도_처리() {
        List<KosisHouseholdRow> r = KosisHouseholdAssembler.assemble(sample("2023", "천원", 1_000, true));
        assertEquals(1_500_000, r.get(1).incomeLow());
        assertEquals("202312", r.get(0).baseYm());          // 연간 -> 12월
    }

    @Test
    void 완전한_가장_최근_시점을_고른다() {
        List<Map<String, String>> rows = new ArrayList<>(sample("202401", "원", 1, false));
        rows.add(row("1분위", "소득", 1, "원", "202402"));   // 최신 시점은 불완전
        assertEquals("202403", KosisHouseholdAssembler.assemble(rows).get(0).baseYm());
    }

    @Test
    void 필요한_항목이_없으면_예외() {
        List<Map<String, String>> rows = new ArrayList<>(sample("202402", "원", 1, false));
        rows.removeIf(m -> m.get("ITM_NM").equals("비소비지출"));
        var e = assertThrows(IllegalStateException.class, () -> KosisHouseholdAssembler.assemble(rows));
        assertTrue(e.getMessage().contains("소비지출"));
    }

    @Test
    void 숫자가_아닌_값은_건너뛴다() {
        List<Map<String, String>> rows = new ArrayList<>(sample("202402", "원", 1, false));
        rows.add(row("1분위", "소득", 0, "원", "202403"));
        rows.get(rows.size() - 1).put("DT", "-");
        assertEquals("202406", KosisHouseholdAssembler.assemble(rows).get(0).baseYm());
    }

    @Test
    void 비율_항목은_건너뛴다() {
        List<Map<String, String>> rows = new ArrayList<>(sample("202402", "원", 1, false));
        rows.add(row("1분위", "소득", 7, "%", "202402")); // 증감률 같은 % 항목
        assertEquals(0, KosisHouseholdAssembler.assemble(rows).get(0).incomeLow());
    }

    @Test
    void 같은_칸에_다른_값이_두개면_예외() {
        List<Map<String, String>> rows = new ArrayList<>(sample("202402", "원", 1, false));
        Map<String, String> real = row("1분위", "소득", 950_000, "원", "202402"); // 예: 실질 금액
        real.put("ITM_NM", "실질");
        real.put("C2_NM", "소득");
        rows.add(real);
        var e = assertThrows(IllegalStateException.class, () -> KosisHouseholdAssembler.assemble(rows));
        assertTrue(e.getMessage().contains("itmId"));
    }

    @Test
    void 분기_시점_변환() {
        assertEquals("202406", KosisHouseholdAssembler.assemble(sample("202402", "원", 1, false)).get(0).baseYm());
        assertEquals("202312", KosisHouseholdAssembler.assemble(sample("202304", "원", 1, false)).get(0).baseYm());
    }
}
