package com.moava.finance.service;

import com.moava.finance.dto.SavingProductComparisonResponse;
import com.moava.finance.dto.SavingProductRow;
import com.moava.finance.mapper.SavingProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** F14 시중 적금 금리 비교 서비스 단위 테스트 */
@ExtendWith(MockitoExtension.class)
class SavingProductServiceTest {

    @Mock
    SavingProductMapper mapper;

    SavingProductService service;

    @BeforeEach
    void setUp() {
        service = new SavingProductService(mapper);
    }

    private static SavingProductRow row(
            long id,
            String bank,
            String name,
            int term,
            String basic,
            String max,
            String fetchedAt
    ) {
        return new SavingProductRow(
                id,
                "00" + id,
                bank,
                name,
                term,
                new BigDecimal(basic),
                max == null ? null : new BigDecimal(max),
                fetchedAt
        );
    }

    @Test
    @DisplayName("요청 만기(12개월)와 일치하는 상품의 평균·최고 금리와 상위 5건을 계산한다")
    void comparesExactTermAndComputesAverages() {
        when(mapper.selectAll()).thenReturn(List.of(
                row(1, "카카오뱅크", "자유적금", 12, "3.20", "3.70", "2026-11-20"),
                row(2, "토스뱅크", "굴비적금", 12, "3.00", "4.50", "2026-11-26"),
                row(3, "신한은행", "청년적금", 12, "3.40", null, "2026-11-25"),
                row(4, "국민은행", "장기적금", 24, "3.50", "4.80", "2026-11-26")
        ));

        SavingProductComparisonResponse res = service.compare(12);

        assertTrue(res.available());
        assertEquals(12, res.requestedTermMonths());
        assertEquals(12, res.matchedTermMonths());
        assertEquals(3, res.productCount());
        assertEquals(new BigDecimal("3.20"), res.avgBasicRate());
        assertEquals(new BigDecimal("3.87"), res.avgMaxRate());
        assertEquals(new BigDecimal("4.50"), res.topMaxRate());
        assertEquals("2026-11-26", res.fetchedAt());
        assertEquals(3, res.topProducts().size());
        assertEquals("토스뱅크", res.topProducts().get(0).bankName());
        assertEquals(new BigDecimal("3.40"), res.topProducts().get(2).rateMax());
    }

    @Test
    @DisplayName("요청 만기(10개월)가 없으면 가장 가까운 만기(12개월) 상품을 선택해 비교한다")
    void picksNearestAvailableTermMonths() {
        when(mapper.selectAll()).thenReturn(List.of(
                row(1, "A은행", "6개월 적금", 6, "2.80", "3.10", "2026-11-25"),
                row(2, "B은행", "12개월 적금", 12, "3.30", "4.10", "2026-11-26")
        ));

        SavingProductComparisonResponse res = service.compare(10);

        assertTrue(res.available());
        assertEquals(10, res.requestedTermMonths());
        assertEquals(12, res.matchedTermMonths());
        assertEquals(1, res.productCount());
        assertEquals("B은행", res.topProducts().get(0).bankName());
    }

    @Test
    @DisplayName("PRODUCTS_SAVING 이 비어 있으면 available=false 를 반환한다")
    void returnsUnavailableWhenEmpty() {
        when(mapper.selectAll()).thenReturn(List.of());

        SavingProductComparisonResponse res = service.compare(12);

        assertFalse(res.available());
        assertEquals(0, res.productCount());
        assertTrue(res.topProducts().isEmpty());
    }

    @Test
    @DisplayName("유효하지 않은 저축 기간(0 이하 또는 120 초과)이면 400 FinanceException 을 던진다")
    void rejectsInvalidTermMonths() {
        assertThrows(FinanceException.class, () -> service.compare(0));
        assertThrows(FinanceException.class, () -> service.compare(121));
    }
}
