package com.moava.finance;

import com.moava.finance.dto.KosisHouseholdRow;
import com.moava.finance.service.DecilePicker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DecilePickerTest {

    // 테스트용 가짜 숫자 (실제 KOSIS 값 아님): 분위마다 하한 100만 원씩
    private static final List<KosisHouseholdRow> ROWS = List.of(
            row(1, 0), row(2, 1_000_000), row(3, 2_000_000), row(4, 3_000_000), row(5, 4_000_000));

    private static KosisHouseholdRow row(int d, long low) {
        return new KosisHouseholdRow(d, low, null, d * 100L, d * 10L, "202401");
    }

    @Test
    void 경계값은_높은_분위에_들어간다() {
        assertEquals(3, DecilePicker.pick(ROWS, 2_000_000).orElseThrow().decile());
    }

    @Test
    void 경계_바로_아래는_낮은_분위() {
        assertEquals(2, DecilePicker.pick(ROWS, 1_999_999).orElseThrow().decile());
    }

    @Test
    void 가장_높은_하한을_넘으면_최고_분위() {
        assertEquals(5, DecilePicker.pick(ROWS, 99_000_000).orElseThrow().decile());
    }

    @Test
    void 수입_0원은_1분위() {
        assertEquals(1, DecilePicker.pick(ROWS, 0).orElseThrow().decile());
    }

    @Test
    void 데이터가_없으면_빈값() {
        assertTrue(DecilePicker.pick(List.of(), 3_000_000).isEmpty());
        assertTrue(DecilePicker.pick(null, 3_000_000).isEmpty());
    }

    @Test
    void 어느_분위에도_못_들면_가장_낮은_분위() {
        List<KosisHouseholdRow> noZero = List.of(row(2, 1_000_000), row(3, 2_000_000));
        assertEquals(2, DecilePicker.pick(noZero, 500_000).orElseThrow().decile());
    }
}
