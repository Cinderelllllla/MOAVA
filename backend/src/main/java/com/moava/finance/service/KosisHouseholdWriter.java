package com.moava.finance.service;

import com.moava.finance.dto.KosisHouseholdRow;
import com.moava.finance.mapper.KosisHouseholdMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** KOSIS_HOUSEHOLD 10줄을 한 번에 저장. 하나라도 실패하면 전부 되돌려서 기존 데이터를 지킨다. */
@Service
public class KosisHouseholdWriter {

    private final KosisHouseholdMapper mapper;

    public KosisHouseholdWriter(KosisHouseholdMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public void saveAll(List<KosisHouseholdRow> rows) {
        for (KosisHouseholdRow r : rows) {
            mapper.upsert(r.decile(), r.incomeLow(), r.incomeHigh(), r.avgConsumption(), r.avgNonconsumption(), r.baseYm());
        }
    }
}
