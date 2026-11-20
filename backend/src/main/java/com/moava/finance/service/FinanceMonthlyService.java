package com.moava.finance.service;

import com.moava.finance.dto.MonthlyListResponse;
import com.moava.finance.dto.MonthlyRecord;
import com.moava.finance.dto.MonthlyUpsertRequest;
import com.moava.finance.mapper.FinanceMonthlyMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/** F10 월별 수입·지출 기록 및 차트용 조회. */
@Service
public class FinanceMonthlyService {

    static final int DEFAULT_MONTHS = 6;
    static final int MAX_MONTHS = 24;
    /** DB 컬럼이 NUMBER(12) 라서 12자리를 넘는 금액은 저장할 수 없다. */
    static final long MAX_AMOUNT = 999_999_999_999L;
    private static final Pattern YEAR_MONTH = Pattern.compile("^\\d{4}(0[1-9]|1[0-2])$");

    private final FinanceMonthlyMapper mapper;

    public FinanceMonthlyService(FinanceMonthlyMapper mapper) {
        this.mapper = mapper;
    }

    /** 최근 months 개월(기록이 있는 달만). 차트용으로 오래된 달 -> 최신 달 순서로 돌려준다. */
    public MonthlyListResponse list(long memberId, Integer months) {
        int limit = months == null ? DEFAULT_MONTHS : months;
        if (limit < 1 || limit > MAX_MONTHS) {
            throw bad("INVALID_MONTHS", "조회 개월 수는 1~" + MAX_MONTHS + " 사이여야 합니다.");
        }
        List<MonthlyRecord> rows = new ArrayList<>(mapper.selectRecent(memberId, limit));
        Collections.reverse(rows); // DB 는 최신순 -> 화면은 월순
        List<MonthlyListResponse.Item> items = rows.stream()
                .map(r -> new MonthlyListResponse.Item(r.yearMonth(), r.income(), r.expense(), r.balance()))
                .toList();
        return new MonthlyListResponse(items);
    }

    /** 같은 달을 다시 저장하면 덮어쓴다 (명세: 같은 월 재입력 시 덮어쓰기). */
    @Transactional
    public MonthlyListResponse.Item upsert(long memberId, MonthlyUpsertRequest req) {
        if (req == null) {
            throw bad("INVALID_REQUEST", "요청 내용이 비어 있습니다.");
        }
        String ym = validYearMonth(req.yearMonth());
        long income = validAmount(req.income());
        long expense = validAmount(req.expense());
        mapper.upsert(memberId, ym, income, expense);
        return new MonthlyListResponse.Item(ym, income, expense, income - expense);
    }

    @Transactional
    public void delete(long memberId, String yearMonth) {
        String ym = validYearMonth(yearMonth);
        if (mapper.delete(memberId, ym) == 0) {
            throw new FinanceException(HttpStatus.NOT_FOUND, "MONTHLY_NOT_FOUND", "해당 월의 기록이 없습니다.");
        }
    }

    static String validYearMonth(String yearMonth) {
        if (yearMonth == null || !YEAR_MONTH.matcher(yearMonth).matches()) {
            throw bad("INVALID_YEAR_MONTH", "연월은 YYYYMM 형식(예: 202611)이어야 합니다.");
        }
        return yearMonth;
    }

    /** 음수·공백·너무 큰 금액은 저장 차단 (명세: 음수 입력 → 저장 차단, 화면 문구 '금액을 확인해 주세요'). */
    static long validAmount(Long amount) {
        if (amount == null || amount < 0 || amount > MAX_AMOUNT) {
            throw bad("INVALID_AMOUNT", "금액을 확인해 주세요.");
        }
        return amount;
    }

    private static FinanceException bad(String code, String message) {
        return new FinanceException(HttpStatus.BAD_REQUEST, code, message);
    }
}
