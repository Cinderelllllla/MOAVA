package com.moava.finance.mapper;

import com.moava.finance.dto.KosisHouseholdRow;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/** KOSIS_HOUSEHOLD (F07) 읽기 전용. 데이터 적재는 별도 수집기(TODO)가 맡는다. */
@Mapper
public interface KosisHouseholdMapper {
    List<KosisHouseholdRow> selectAll();
}
