package com.moava.openbanking;

import org.springframework.stereotype.Component;

import java.util.List;

import com.moava.openbanking.OpenBankingDTO.CodefAccountListRequest;
import com.moava.openbanking.OpenBankingDTO.CodefAccountSummary;
import com.moava.openbanking.OpenBankingDTO.CodefRegistrationStatusRequest;
import com.moava.openbanking.OpenBankingDTO.CodefRegistrationStatusResponse;
import com.moava.openbanking.OpenBankingDTO.CodefTransactionItem;
import com.moava.openbanking.OpenBankingDTO.CodefTransactionListRequest;

@Component
public class RealCodefClient implements CodefClient {

    @Override
    public CodefRegistrationStatusResponse checkRegistrationStatus(CodefRegistrationStatusRequest request) {
        throw new UnsupportedOperationException("실제 CODEF 연동은 아직 구현되지 않았습니다. 데모용 MockCodefClient를 사용하세요.");
    }

    @Override
    public List<CodefAccountSummary> fetchAccountList(CodefAccountListRequest request) {
        throw new UnsupportedOperationException("실제 CODEF 연동은 아직 구현되지 않았습니다. 데모용 MockCodefClient를 사용하세요.");
    }

    @Override
    public List<CodefTransactionItem> fetchTransactionList(CodefTransactionListRequest request) {
        throw new UnsupportedOperationException("실제 CODEF 연동은 아직 구현되지 않았습니다. 데모용 MockCodefClient를 사용하세요.");
    }
}
