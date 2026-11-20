package com.moava.mockbank;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

public class MockBankDto {

    @Getter @Setter
    public static class CertRegisterRequest {
        private Long userId;
        private String publicKey; // 브라우저 Web Crypto에서 추출한 SPKI Base64
    }

    @Getter @Setter
    public static class CertIssueResponse {
        private String certSerialNo;
        private LocalDateTime issuedAt;
        private LocalDateTime expiresAt;
    }

    @Getter @Setter
    public static class CertificateRecord {
        private Long certId;
        private Long userId;
        private String serialNo;
        private String publicKey;
        private String status;
        private LocalDateTime issuedAt;
        private LocalDateTime expiresAt;
    }

    @Getter @Setter
    public static class ChallengeRequest {
        private Long userId;
        private String scope;      // CREDIT_ONCE 또는 GROUP_LEDGER_READ
        private String accountNo;
    }

    @Getter @Setter
    public static class ChallengeResponse {
        private String nonce;
        private String consentText;
        private LocalDateTime expiresAt;
    }

    @Getter @Setter
    public static class ChallengeRecord {
        private String nonce;
        private Long userId;
        private String accountNo;
        private String scope;
        private String consentText;
        private String usedYn;
        private LocalDateTime expiresAt;
    }

    @Getter @Setter
    public static class VerifySignatureRequest {
        private String certSerialNo;
        private String nonce;
        private String signature;               // RSA 서명 Base64
        private String accountNo;
        private LocalDateTime activityStartedAt; // GROUP_LEDGER_READ일 때만 사용 (미입력 시 현재시각)
    }

    @Getter @Setter
    public static class TokenResponse {
        private String accessToken;
        private String scope;
        private LocalDateTime validFrom;
        private LocalDateTime expiresAt;
    }

    @Getter @Setter
    public static class ConsentTokenRecord {
        private String tokenId;
        private Long userId;
        private String accountNo;
        private String scope;
        private LocalDateTime validFrom;
        private String usedYn;
        private String signatureHash;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
    }

    @Getter @Setter
    public static class TransactionDto {
        private String bankTxId;
        private String accountNo;
        private String txType;
        private Long amount;
        private Long balanceAfter;
        private String counterparty;
        private LocalDateTime txAt;
    }
}