package com.moava.finance;

import com.moava.finance.dto.KosisHouseholdRow;
import com.moava.finance.mapper.KosisHouseholdMapper;
import com.moava.finance.service.FinanceException;
import com.moava.finance.service.SpendingDefaultService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpendingDefaultServiceTest {

    @Mock KosisHouseholdMapper mapper;
    @InjectMocks SpendingDefaultService service;

    @Test
    void KOSIS_데이터가_비어_있으면_available_false() {
        when(mapper.selectAll()).thenReturn(List.of());
        assertFalse(service.suggest(3_000_000L).available());
    }

    @Test
    void 월수입이_속한_분위의_평균을_돌려준다() {
        // 테스트용 가짜 숫자
        when(mapper.selectAll()).thenReturn(List.of(
                new KosisHouseholdRow(1, 0, 999_999L, 111, 11, "202401"),
                new KosisHouseholdRow(2, 1_000_000, null, 222, 22, "202401")));
        var r = service.suggest(1_500_000L);
        assertTrue(r.available());
        assertEquals(2, r.decile());
        assertEquals(222, r.avgConsumption());
        assertEquals(22, r.avgNonconsumption());
    }

    @Test
    void 음수나_null_수입은_400() {
        assertEquals(400, assertThrows(FinanceException.class, () -> service.suggest(-1L)).getStatus().value());
        assertThrows(FinanceException.class, () -> service.suggest(null));
    }
}
