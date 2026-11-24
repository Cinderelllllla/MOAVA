package com.moava.openbanking.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * MyBatis Oracle DB 매핑용 내부 레코드 DTO
 */
public class KftcRecordDto {

    @Getter @Setter
    public static class AccountRecord {
        private String fintechUseNum;
        private String bankCode;
        private String bankName;
        private String accountNo;
        private String holderName;
        private String userSeqNo;
        private Long balance;
        private String leaderYn;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Getter @Setter
    public static class TransactionRecord {
        private Long txId;
        private String bankTranId;
        private String fintechUseNum;
        private String inoutType;
        private Long tranAmt;
        private Long afterBalanceAmt;
        private String printContent;
        private String counterpartyName;
        private String counterpartyFinNum;
        private String branchName;
        private LocalDateTime tranAt;
    }

    @Getter @Setter
    public static class OAuthCodeRecord {
        private String authCode;
        private String clientId;
        private String userSeqNo;
        private String scope;
        private String redirectUri;
        private String usedYn;
        private LocalDateTime expiresAt;
        private LocalDateTime createdAt;
    }

    @Getter @Setter
    public static class OAuthTokenRecord {
        private String tokenHash;
        private String clientId;
        private String userSeqNo;
        private String scope;
        private String revokedYn;
        private LocalDateTime expiresAt;
        private LocalDateTime createdAt;
    }
}
