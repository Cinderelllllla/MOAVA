package com.moava.finance.mapper;

import com.moava.finance.dto.MonthlyRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** FINANCE_MONTHLY (F10). 모든 쿼리에 memberId 조건이 붙어서 남의 기록은 건드릴 수 없다. */
@Mapper
public interface FinanceMonthlyMapper {

    /** 최신 달부터 limit 개 (최신순). */
    List<MonthlyRecord> selectRecent(@Param("memberId") long memberId, @Param("limit") int limit);

    /** 같은 (회원, 연월)이 있으면 수정, 없으면 추가. */
    int upsert(@Param("memberId") long memberId, @Param("yearMonth") String yearMonth,
               @Param("income") long income, @Param("expense") long expense);

    int delete(@Param("memberId") long memberId, @Param("yearMonth") String yearMonth);
}
