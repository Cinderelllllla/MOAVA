package com.moava.openbanking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

public class KftcAccountDto {

    @Getter @Setter
    public static class RealNameRequest {
        @JsonProperty("bank_tran_id")
        private String bankTranId;

        @JsonProperty("bank_code_std")
        private String bankCodeStd;

        @JsonProperty("account_num")
        private String accountNum;

        @JsonProperty("account_holder_name")
        private String accountHolderName;

        @JsonProperty("tran_dtime")
        private String tranDtime;
    }

    @Getter @Setter
    public static class RealNameResponse extends KftcBaseResponse {
        @JsonProperty("bank_tran_id")
        private String bankTranId;

        @JsonProperty("bank_code_std")
        private String bankCodeStd;

        @JsonProperty("bank_name")
        private String bankName;

        @JsonProperty("account_num")
        private String accountNum;

        @JsonProperty("account_holder_name")
        private String accountHolderName;

        @JsonProperty("fintech_use_num")
        private String fintechUseNum;

        @JsonProperty("leader_account_yn")
        private String leaderAccountYn;

        @JsonProperty("verified")
        private boolean verified;
    }

    @Getter @Setter
    public static class BalanceResponse extends KftcBaseResponse {
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

        @JsonProperty("available_amt")
        private Long availableAmt;
    }
}
