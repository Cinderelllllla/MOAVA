package com.moava.openbanking.codef;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

public class CodefDto {

    @Getter @Setter
    public static class SupportedBank {
        private String organization;
        private String kftcCode;
        private String bankName;

        public SupportedBank(String organization, String kftcCode, String bankName) {
            this.organization = organization;
            this.kftcCode = kftcCode;
            this.bankName = bankName;
        }
    }

    /**
     * CODEF 개인 은행 계정 연결 요청 (/v1/account/create)
     * - loginType "0": 공동인증서 (derFile + keyFile 또는 PFX certFile + password)
     * - loginType "1": 은행 인터넷뱅킹 아이디/비밀번호 (id + password)
     * ※ CODEF 은행 개인 수시입출 API는 간편인증(loginType=5/6)을 지원하지 않음
     */
    @Getter @Setter
    public static class ConnectRequest {
        private String organization; // 4자리 기관코드 또는 은행명 (예: "0004" 또는 "국민은행")
        private String loginType;    // "0"(공동인증서) 또는 "1"(은행 아이디/비밀번호)
        private String certType;     // 공동인증서 구분 ("1": der/key 기본, "pfx": certFile)
        private String derFile;      // Base64 인코딩된 signCert.der
        private String keyFile;      // Base64 인코딩된 signPri.key
        private String certFile;     // Base64 인코딩된 PFX(.p12/.pfx)
        private String id;           // loginType="1" 은행 로그인 아이디
        private String password;     // 인증서 비밀번호 또는 은행 로그인 비밀번호 (서버에서 RSA 암호화 후 폐기)
        private String birthDate;    // YYMMDD (일부 은행 조건부)
    }

    @Getter @Setter
    public static class ConnectResponse {
        private String organization;
        private String bankName;
        private boolean connected;
        private String maskedConnectedId;
        private String message;
    }

    @Getter @Setter
    public static class AccountListRequest {
        private String organization;
        private String birthDate;
    }

    @Getter @Setter
    public static class AccountSummary {
        private String accountNo;
        private String accountDisplay;
        private String accountName;
        private Long balanceAmt;
        private String depositType;
    }

    @Getter @Setter
    public static class AccountListResponse {
        private String organization;
        private String bankName;
        private int count;
        private List<AccountSummary> accounts;
    }

    /**
     * CODEF 개인 수시입출 거래내역 조회(inquiryType=1) 기반 실계좌인증 요청
     */
    @Getter @Setter
    public static class VerifyAccountRequest {
        private String organization;       // 4자리 기관코드 또는 은행명
        private String account;            // 계좌번호 (숫자 또는 하이픈 포함 허용, 전송 시 숫자만)
        private String accountPassword;    // 계좌 비밀번호 (은행별 조건부, RSA 암호화 후 폐기)
        private String birthDate;          // 생년월일 YYMMDD (은행별 조건부)
        private String startDate;          // yyyyMMdd (미입력 시 최근 1개월)
        private String endDate;            // yyyyMMdd (미입력 시 오늘)
        private Boolean syncToMockBank;    // true(기본)/null: 인증 성공 시 모의 은행 원장에 자동 적재, false: CODEF 인증만 수행
        private Long initialMockBalance;   // 선택: 모의 은행 적재 잔액 지정 (미입력 시 CODEF 실잔액 반영)
        private Boolean leaderAccount;     // 선택: 모임장 수취 계좌 여부 (미입력 시 GYE_GROUP.OWNER_ID 보유 여부로 자동 판별)
    }

    @Getter @Setter
    public static class VerifyAccountResponse {
        private boolean verified;
        private String organization;
        private String bankName;
        private String accountName;        // CODEF 조회 계좌 상품명 (resAccountName)
        private String accountMasked;
        private String accountLast4;
        private String holderName;         // CODEF 실조회 예금주명 (resAccountHolder)
        private String memberName;         // MOAVA 회원 실명 (MEMBER.NAME)
        private Long balanceAmt;
        private int recentTransactionCount;
        private LocalDateTime verifiedAt;
        private com.moava.openbanking.dto.MockBankDto.ProvisionResult mockAccount;
        private String message;
    }

    @Getter @Setter
    public static class TransactionRequest {
        private String organization;
        private String account;            // 생략 시 해당 은행의 인증 완료된 계좌 사용
        private String accountPassword;
        private String birthDate;
        private String startDate;          // yyyyMMdd (미입력 시 최근 1개월)
        private String endDate;            // yyyyMMdd (미입력 시 오늘)
        private String orderBy;            // "0" 최신순(기본), "1" 과거순
    }

    @Getter @Setter
    public static class TransactionItem {
        private String tranDate;           // yyyyMMdd
        private String tranTime;           // HHmmss
        private String inoutType;          // 입금 / 출금
        private Long tranAmt;
        private Long afterBalanceAmt;
        private String desc1;
        private String desc2;
        private String desc3;
        private String desc4;
    }

    @Getter @Setter
    public static class TransactionListResponse {
        private String organization;
        private String bankName;
        private String accountMasked;
        private String holderName;
        private Long balanceAmt;
        private String startDate;
        private String endDate;
        private int count;
        private List<TransactionItem> transactions;
    }

    @Getter @Setter
    public static class ConnectionSummary {
        private String organization;
        private String bankName;
        private boolean verified;
        private String verifiedAccountMasked;
        private String verifiedHolderName;
        private LocalDateTime verifiedAt;
        private LocalDateTime updatedAt;
    }

    @Getter @Setter
    public static class ConnectionRecord {
        private Long connId;
        private Long memberId;
        private String organization;
        private String connectedIdEnc;
        private Integer keyVer;
        private String verifiedYn;
        private String verifiedAccountEnc;
        private String verifiedAccountLast4;
        private String verifiedHolderName;
        private LocalDateTime verifiedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Getter @Setter
    public static class ErrorResponse {
        private String code;
        private String message;

        public ErrorResponse(String code, String message) {
            this.code = code;
            this.message = message;
        }
    }
}
