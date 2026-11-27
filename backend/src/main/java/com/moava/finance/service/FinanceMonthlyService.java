package com.moava.finance.service;

import com.moava.finance.dto.MonthlyBatchRequest;
import com.moava.finance.dto.MonthlyListResponse;
import com.moava.finance.dto.MonthlyRecord;
import com.moava.finance.dto.MonthlyUpsertRequest;
import com.moava.finance.mapper.FinanceMonthlyMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** F10 월별 수입·지출 기록 및 차트용 조회. */
@Service
public class FinanceMonthlyService {

    static final int DEFAULT_MONTHS = 6;
    static final int MAX_MONTHS = 24;
    /** DB 컬럼이 NUMBER(12) 라서 12자리를 넘는 금액은 저장할 수 없다. */
    static final long MAX_AMOUNT = 999_999_999_999L;
    private static final Pattern YEAR_MONTH = Pattern.compile("^\\d{4}(0[1-9]|1[0-2])$");
    /** 저장할 수 있는 달: 오늘(한국 시간) 기준 최근 12개월 = 11개월 전 ~ 이번 달 (화면 대상 월 목록과 같음) */
    static final int SAVE_WINDOW_MONTHS = 12;
    /** 한 번에 저장할 수 있는 달 수 */
    static final int MAX_BATCH = 12;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    private final FinanceMonthlyMapper mapper;
    private final Clock clock;

    @Autowired
    public FinanceMonthlyService(FinanceMonthlyMapper mapper) {
        this(mapper, Clock.system(SEOUL));
    }

    /** 테스트에서 "오늘"을 고정할 때 쓴다 */
    public FinanceMonthlyService(FinanceMonthlyMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
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
        MonthlyListResponse.Item item = validItem(req);
        mapper.upsert(memberId, item.yearMonth(), item.income(), item.expense());
        return item;
    }

    /**
     * 여러 달 한 번에 저장 (MOA-102). 검사는 단건 저장과 같다(validItem).
     * 하나라도 틀리면 아무것도 저장하지 않는다: 모두 검사한 뒤에 저장하고, 저장 중 실패하면 트랜잭션이 되돌린다.
     */
    @Transactional
    public MonthlyListResponse upsertBatch(long memberId, MonthlyBatchRequest req) {
        List<MonthlyUpsertRequest> items = req == null ? null : req.items();
        if (items == null || items.isEmpty() || items.size() > MAX_BATCH) {
            throw bad("INVALID_BATCH", "한 번에 1~" + MAX_BATCH + "개월까지 저장할 수 있어요.");
        }
        List<MonthlyListResponse.Item> valid = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            String where = (i + 1) + "번째";
            MonthlyUpsertRequest row = items.get(i);
            MonthlyListResponse.Item item;
            try {
                item = validItem(row);
            } catch (FinanceException e) {
                String month = row != null && row.yearMonth() != null ? "(" + monthText(row.yearMonth()) + ")" : "";
                throw bad(e.getCode(), where + month + ": " + e.getMessage());
            }
            if (!seen.add(item.yearMonth())) {
                throw bad("DUPLICATE_YEAR_MONTH", where + "(" + monthText(item.yearMonth()) + "): 같은 달이 두 번 들어 있어요.");
            }
            valid.add(item);
        }
        for (MonthlyListResponse.Item item : valid) {
            mapper.upsert(memberId, item.yearMonth(), item.income(), item.expense());
        }
        return new MonthlyListResponse(valid);
    }

    /** 저장 한 건 검사: 연월 형식 + 최근 12개월 + 금액 */
    private MonthlyListResponse.Item validItem(MonthlyUpsertRequest req) {
        if (req == null) {
            throw bad("INVALID_REQUEST", "요청 내용이 비어 있습니다.");
        }
        String ym = validSaveMonth(req.yearMonth());
        long income = validAmount(req.income());
        long expense = validAmount(req.expense());
        return new MonthlyListResponse.Item(ym, income, expense, income - expense);
    }

    /** 저장 가능한 연월: 형식(YYYYMM) + 오늘 기준 최근 12개월 안 */
    String validSaveMonth(String yearMonth) {
        String ym = validYearMonth(yearMonth);
        YearMonth target = YearMonth.parse(ym, YM);
        YearMonth now = YearMonth.now(clock);
        YearMonth first = now.minusMonths(SAVE_WINDOW_MONTHS - 1L);
        if (target.isBefore(first) || target.isAfter(now)) {
            throw bad("OUT_OF_RANGE_MONTH", "최근 12개월(" + monthText(first.format(YM)) + " ~ " + monthText(now.format(YM)) + ")만 저장할 수 있어요.");
        }
        return ym;
    }

    /** "202512" → "2025년 12월" */
    static String monthText(String yearMonth) {
        if (yearMonth == null || !YEAR_MONTH.matcher(yearMonth).matches()) {
            return String.valueOf(yearMonth);
        }
        return yearMonth.substring(0, 4) + "년 " + Integer.parseInt(yearMonth.substring(4)) + "월";
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
