package com.moava.finance;

import com.moava.finance.dto.MonthlyListResponse;
import com.moava.finance.dto.MonthlyRecord;
import com.moava.finance.dto.MonthlyUpsertRequest;
import com.moava.finance.mapper.FinanceMonthlyMapper;
import com.moava.finance.service.FinanceException;
import com.moava.finance.service.FinanceMonthlyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinanceMonthlyServiceTest {

    @Mock FinanceMonthlyMapper mapper;
    @InjectMocks FinanceMonthlyService service;

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
