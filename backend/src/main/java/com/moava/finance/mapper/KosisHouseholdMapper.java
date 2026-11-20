package com.moava.finance.mapper;

import com.moava.finance.dto.KosisHouseholdRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** KOSIS_HOUSEHOLD (F07). 조회는 SpendingDefaultService, 저장은 KosisHouseholdCollector 가 쓴다. */
@Mapper
public interface KosisHouseholdMapper {
    List<KosisHouseholdRow> selectAll();

    /** 분위(DECILE) 기준 Upsert. incomeHigh 는 10분위일 때 null. */
    int upsert(@Param("decile") int decile,
               @Param("incomeLow") long incomeLow,
               @Param("incomeHigh") Long incomeHigh,
               @Param("avgConsumption") long avgConsumption,
               @Param("avgNonconsumption") long avgNonconsumption,
               @Param("baseYm") String baseYm);
}
