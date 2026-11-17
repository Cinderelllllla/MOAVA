package com.moava.openbanking;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import com.moava.openbanking.OpenBankingDTO.CodefAccountListRequest;
import com.moava.openbanking.OpenBankingDTO.CodefAccountSummary;
import com.moava.openbanking.OpenBankingDTO.CodefRegistrationStatusRequest;
import com.moava.openbanking.OpenBankingDTO.CodefRegistrationStatusResponse;
import com.moava.openbanking.OpenBankingDTO.CodefTransactionItem;
import com.moava.openbanking.OpenBankingDTO.CodefTransactionListRequest;

@Component
public class MockCodefClient implements CodefClient {

    @Override
    public CodefRegistrationStatusResponse checkRegistrationStatus(CodefRegistrationStatusRequest request) {
        return CodefRegistrationStatusResponse.builder()
                .registered(true)
                .status("REGISTERED")
                .message("CODEF 계좌 연동 상태입니다.")
                .bankCode(request.getBankCode() == null ? "099" : request.getBankCode())
                .accountNo("000000000000")
                .build();
    }

    @Override
    public List<CodefAccountSummary> fetchAccountList(CodefAccountListRequest request) {
        return List.of(
                CodefAccountSummary.builder()
                        .bankCode(request.getBankCode() == null ? "099" : request.getBankCode())
                        .bankName("MOAVA_BANK")
                        .accountNo("000000000000")
                        .accountName("MOAVA_DEMO_ACCOUNT")
                        .accountType("CHECKING")
                        .fintechUseNum("199000000000000000000000")
                        .isPrimary(true)
                        .build()
        );
    }

    @Override
    public List<CodefTransactionItem> fetchTransactionList(CodefTransactionListRequest request) {
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return List.of(
                CodefTransactionItem.builder()
                        .transactionDate(today)
                        .transactionTime("090000")
                        .inOutType("DEPOSIT")
                        .amount(100000L)
                        .balanceAfter(100000L)
                        .counterpartyName("MOAVA")
                        .printContent("회비 납입")
                        .transactionId(UUID.randomUUID().toString())
                        .build()
        );
    }
}
