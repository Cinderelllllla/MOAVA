package com.moava.finance.mapper;

import com.moava.finance.dto.SavingProductRow;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/** F14 금감원 적금 금리 캐시(PRODUCTS_SAVING) 조회 매퍼 */
@Mapper
public interface SavingProductMapper {
    List<SavingProductRow> selectAll();
}
