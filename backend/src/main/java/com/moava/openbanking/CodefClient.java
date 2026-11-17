package com.moava.openbanking;

import com.moava.openbanking.OpenBankingDTO.CodefAccountListRequest;
import com.moava.openbanking.OpenBankingDTO.CodefAccountSummary;
import com.moava.openbanking.OpenBankingDTO.CodefRegistrationStatusRequest;
import com.moava.openbanking.OpenBankingDTO.CodefRegistrationStatusResponse;
import com.moava.openbanking.OpenBankingDTO.CodefTransactionItem;
import com.moava.openbanking.OpenBankingDTO.CodefTransactionListRequest;

import java.util.List;

public interface CodefClient {

    CodefRegistrationStatusResponse checkRegistrationStatus(CodefRegistrationStatusRequest request);

    List<CodefAccountSummary> fetchAccountList(CodefAccountListRequest request);

    List<CodefTransactionItem> fetchTransactionList(CodefTransactionListRequest request);
}
