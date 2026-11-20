package com.moava.finance.service;

import com.moava.finance.dto.KosisHouseholdRow;
import com.moava.finance.dto.SpendingDefaultResponse;
import com.moava.finance.mapper.KosisHouseholdMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * F07 지출 미입력 기본값 제안.
 * 월수입으로 소득 분위를 찾아 그 분위의 KOSIS 평균 소비·비소비지출을 알려 준다.
 * KOSIS_HOUSEHOLD 가 비어 있으면 available=false 를 돌려주고, 화면은 빈칸을 유지한다.
 *
 * 데이터 적재는 KosisHouseholdCollector 가 맡는다 (기동 시 + 매월 1일).
 */
@Service
public class SpendingDefaultService {

    private final KosisHouseholdMapper mapper;

    public SpendingDefaultService(KosisHouseholdMapper mapper) {
        this.mapper = mapper;
    }

    /** @param income 월수입 (원) */
    public SpendingDefaultResponse suggest(Long income) {
        if (income == null || income < 0) {
            throw new FinanceException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "금액을 확인해 주세요.");
        }
        List<KosisHouseholdRow> rows = mapper.selectAll();
        return DecilePicker.pick(rows, income)
                .map(r -> new SpendingDefaultResponse(true, r.decile(), r.avgConsumption(), r.avgNonconsumption(), r.baseYm()))
                .orElseGet(SpendingDefaultResponse::unavailable);
    }
}
