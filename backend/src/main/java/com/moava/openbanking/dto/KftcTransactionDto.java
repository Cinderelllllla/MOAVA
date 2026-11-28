package com.moava.openbanking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class KftcTransactionDto {

    @Getter @Setter
    public static class TransactionListResponse extends KftcBaseResponse {
        @JsonProperty("bank_tran_id")
        private String bankTranId;

        @JsonProperty("bank_code_std")
        private String bankCodeStd;

        @JsonProperty("bank_name")
        private String bankName;

        @JsonProperty("fintech_use_num")
        private String fintechUseNum;

        @JsonProperty("account_holder_name")
        private String accountHolderName;

        @JsonProperty("balance_amt")
        private Long balanceAmt;

        @JsonProperty("page_record_cnt")
        private int pageRecordCnt;

        @JsonProperty("res_list")
        private List<TransactionItem> resList;
    }

    @Getter @Setter
    public static class TransactionItem {
        @JsonProperty("bank_tran_id")
        private String bankTranId;

        @JsonProperty("tran_date")
        private String tranDate; // yyyyMMdd

        @JsonProperty("tran_time")
        private String tranTime; // HHmmss

        @JsonProperty("inout_type")
        private String inoutType; // 입금 / 출금

        @JsonProperty("tran_type")
        private String tranType;

        @JsonProperty("print_content")
        private String printContent; // 통장 적요 (인자 내역)

        @JsonProperty("tran_amt")
        private Long tranAmt;

        @JsonProperty("after_balance_amt")
        private Long afterBalanceAmt;

        @JsonProperty("branch_name")
        private String branchName;

        @JsonProperty("counterparty_name")
        private String counterpartyName;
    }
}
