package com.moava.finance;

import com.moava.finance.dto.MonthlyBatchRequest;
import com.moava.finance.dto.MonthlyListResponse;
import com.moava.finance.dto.MonthlyRecord;
import com.moava.finance.dto.MonthlyUpsertRequest;
import com.moava.finance.mapper.FinanceMonthlyMapper;
import com.moava.finance.service.FinanceException;
import com.moava.finance.service.FinanceMonthlyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinanceMonthlyServiceTest {

    @Mock FinanceMonthlyMapper mapper;
    FinanceMonthlyService service;

    /** "오늘"을 2026-11-27(한국 시간)로 고정 → 저장 가능한 달은 2025년 12월 ~ 2026년 11월 */
    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(Instant.parse("2026-11-27T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        service = new FinanceMonthlyService(mapper, fixed);
    }

    private static MonthlyUpsertRequest row(String ym, long income, long expense) {
        return new MonthlyUpsertRequest(ym, income, expense);
    }

    // ---- 최근 12개월 범위 (MOA-102) ----

    @Test
    void 단건_저장도_최근_12개월_밖이면_400() {
        assertDoesNotThrow(() -> service.upsert(7L, row("202512", 1L, 1L)));   // 첫 달
        assertDoesNotThrow(() -> service.upsert(7L, row("202611", 1L, 1L)));   // 이번 달
        for (String out : new String[]{"202511", "202612"}) {
            FinanceException e = assertThrows(FinanceException.class, () -> service.upsert(7L, row(out, 1L, 1L)));
            assertEquals("OUT_OF_RANGE_MONTH", e.getCode());
            assertTrue(e.getMessage().contains("2025년 12월 ~ 2026년 11월"));
        }
        verify(mapper, never()).upsert(anyLong(), eq("202511"), anyLong(), anyLong());
        verify(mapper, never()).upsert(anyLong(), eq("202612"), anyLong(), anyLong());
    }

    // ---- 여러 달 한 번에 저장 (MOA-102) ----

    @Test
    void 일괄_저장_정상_3건() {
        MonthlyListResponse res = service.upsertBatch(7L, new MonthlyBatchRequest(List.of(
                row("202512", 3_000_000L, 2_000_000L),
                row("202601", 3_100_000L, 2_100_000L),
                row("202611", 3_200_000L, 2_200_000L))));

        assertEquals(3, res.items().size());
        assertEquals("202601", res.items().get(1).yearMonth());
        assertEquals(1_000_000L, res.items().get(1).balance());
        verify(mapper).upsert(7L, "202512", 3_000_000L, 2_000_000L);
        verify(mapper).upsert(7L, "202601", 3_100_000L, 2_100_000L);
        verify(mapper).upsert(7L, "202611", 3_200_000L, 2_200_000L);
    }

    @Test
    void 일괄_저장_같은_달이_두_번이면_400_이고_아무것도_저장하지_않는다() {
        FinanceException e = assertThrows(FinanceException.class, () -> service.upsertBatch(7L, new MonthlyBatchRequest(List.of(
                row("202601", 1L, 1L), row("202602", 1L, 1L), row("202601", 2L, 2L)))));
        assertEquals(400, e.getStatus().value());
        assertEquals("DUPLICATE_YEAR_MONTH", e.getCode());
        assertTrue(e.getMessage().startsWith("3번째(2026년 1월)"));
        verifyNoInteractions(mapper);
    }

    @Test
    void 일괄_저장_범위_밖_달이_하나라도_있으면_400_이고_아무것도_저장하지_않는다() {
        FinanceException e = assertThrows(FinanceException.class, () -> service.upsertBatch(7L, new MonthlyBatchRequest(List.of(
                row("202610", 1L, 1L), row("202511", 1L, 1L)))));
        assertEquals("OUT_OF_RANGE_MONTH", e.getCode());
        assertTrue(e.getMessage().startsWith("2번째(2025년 11월): "));
        verifyNoInteractions(mapper);
    }

    @Test
    void 일괄_저장_금액이_틀리면_몇_번째인지_알려_준다() {
        FinanceException e = assertThrows(FinanceException.class, () -> service.upsertBatch(7L, new MonthlyBatchRequest(List.of(
                row("202610", 1L, 1L), row("202611", -1L, 1L)))));
        assertEquals("INVALID_AMOUNT", e.getCode());
        assertEquals("2번째(2026년 11월): 금액을 확인해 주세요.", e.getMessage());
        verifyNoInteractions(mapper);
    }

    @Test
    void 일괄_저장은_1에서_12건() {
        List<MonthlyUpsertRequest> thirteen = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            thirteen.add(row("202611", 1L, 1L));
        }
        assertEquals("INVALID_BATCH", assertThrows(FinanceException.class,
                () -> service.upsertBatch(7L, new MonthlyBatchRequest(thirteen))).getCode());
        assertEquals("INVALID_BATCH", assertThrows(FinanceException.class,
                () -> service.upsertBatch(7L, new MonthlyBatchRequest(List.of()))).getCode());
        assertEquals("INVALID_BATCH", assertThrows(FinanceException.class,
                () -> service.upsertBatch(7L, null)).getCode());
        verifyNoInteractions(mapper);
    }

    @Test
    void 목록은_오래된_달부터_순서로_돌려준다() {
        when(mapper.selectRecent(1L, 6)).thenReturn(List.of(
                new MonthlyRecord("202611", 3_200_000, 2_000_000),
                new MonthlyRecord("202610", 3_000_000, 3_100_000)));
        var res = service.list(1L, null);
        assertEquals("202610", res.items().get(0).yearMonth());
        assertEquals("202611", res.items().get(1).yearMonth());
        assertEquals(-100_000, res.items().get(0).balance());   // 적자는 음수
        assertEquals(1_200_000, res.items().get(1).balance());
    }

    @Test
    void 개월수가_범위를_벗어나면_400() {
        assertEquals(400, assertThrows(FinanceException.class, () -> service.list(1L, 0)).getStatus().value());
        assertThrows(FinanceException.class, () -> service.list(1L, 25));
        verifyNoInteractions(mapper);
    }

    @Test
    void 저장하면_원단위로_upsert_한다() {
        MonthlyListResponse.Item r = service.upsert(7L, new MonthlyUpsertRequest("202611", 3_200_000L, 2_080_000L));
        verify(mapper).upsert(7L, "202611", 3_200_000L, 2_080_000L);
        assertEquals(1_120_000, r.balance());
    }

    @Test
    void 음수_공백_너무큰_금액은_저장_차단() {
        assertThrows(FinanceException.class, () -> service.upsert(1L, new MonthlyUpsertRequest("202611", -1L, 0L)));
        assertThrows(FinanceException.class, () -> service.upsert(1L, new MonthlyUpsertRequest("202611", null, 0L)));
        assertThrows(FinanceException.class, () -> service.upsert(1L, new MonthlyUpsertRequest("202611", 0L, 1_000_000_000_000L)));
        verify(mapper, never()).upsert(anyLong(), anyString(), anyLong(), anyLong());
    }

    @Test
    void 연월_형식이_틀리면_차단() {
        for (String bad : new String[]{null, "", "2026-11", "202613", "202600", "20261", "11월"}) {
            assertThrows(FinanceException.class, () -> service.upsert(1L, new MonthlyUpsertRequest(bad, 0L, 0L)), String.valueOf(bad));
        }
    }

    @Test
    void 없는_달_삭제는_404() {
        when(mapper.delete(1L, "202601")).thenReturn(0);
        assertEquals(404, assertThrows(FinanceException.class, () -> service.delete(1L, "202601")).getStatus().value());
    }

    @Test
    void 있는_달_삭제는_성공() {
        when(mapper.delete(1L, "202601")).thenReturn(1);
        assertDoesNotThrow(() -> service.delete(1L, "202601"));
    }
}
