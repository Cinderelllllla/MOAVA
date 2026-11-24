package com.moava.openbanking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

public class KftcTransferDto {

    @Getter @Setter
    public static class WithdrawRequest {
        @JsonProperty("bank_tran_id")
        private String bankTranId; // 20자리 고유번호 (미입력 시 서버 자동 채번)

        @JsonProperty("fintech_use_num")
        private String fintechUseNum; // 출금 계좌 핀테크이용번호

        @JsonProperty("recv_fintech_use_num")
        private String recvFintechUseNum; // 입금(모임장) 계좌 핀테크이용번호

        @JsonProperty("dps_print_content")
        private String dpsPrintContent; // 입금계좌 통장 적요

        @JsonProperty("wd_print_content")
        private String wdPrintContent; // 출금계좌 통장 적요

        @JsonProperty("tran_amt")
        private Long tranAmt; // 이체 금액 (원, 1 이상)

        @JsonProperty("req_client_name")
        private String reqClientName;

        @JsonProperty("tran_dtime")
        private String tranDtime;
    }

    @Getter @Setter
    public static class DepositRequest {
        @JsonProperty("bank_tran_id")
        private String bankTranId; // 20자리 고유번호 (미입력 시 서버 자동 채번)

        @JsonProperty("wd_fintech_use_num")
        private String wdFintechUseNum; // 출금 계좌 핀테크이용번호

        @JsonProperty("fintech_use_num")
        private String fintechUseNum; // 입금(수취 모임장) 계좌 핀테크이용번호

        @JsonProperty("print_content")
        private String printContent; // 입금계좌 통장 적요

        @JsonProperty("wd_print_content")
        private String wdPrintContent; // 출금계좌 통장 적요

        @JsonProperty("tran_amt")
        private Long tranAmt;

        @JsonProperty("req_client_name")
        private String reqClientName;

        @JsonProperty("tran_dtime")
        private String tranDtime;
    }

    @Getter @Setter
    public static class TransferResponse extends KftcBaseResponse {
        @JsonProperty("bank_tran_id")
        private String bankTranId;

        @JsonProperty("bank_tran_date")
        private String bankTranDate;

        @JsonProperty("wd_fintech_use_num")
        private String wdFintechUseNum;

        @JsonProperty("wd_bank_name")
        private String wdBankName;

        @JsonProperty("wd_account_holder_name")
        private String wdAccountHolderName;

        @JsonProperty("wd_after_balance_amt")
        private Long wdAfterBalanceAmt;

        @JsonProperty("dps_fintech_use_num")
        private String dpsFintechUseNum;

        @JsonProperty("dps_bank_name")
        private String dpsBankName;

        @JsonProperty("dps_account_holder_name")
        private String dpsAccountHolderName;

        @JsonProperty("dps_after_balance_amt")
        private Long dpsAfterBalanceAmt;

        @JsonProperty("tran_amt")
        private Long tranAmt;

        @JsonProperty("print_content")
        private String printContent;
    }
}
