package com.moava.openbanking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class OpenBankingDTO {

    /**
     * 1. CODEF 계좌 정보 동기화/적재 및 잔액 세팅 요청 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodefSyncRequest {
        private Long memberId;          // 회원 ID
        private String bankCode;        // 은행코드 (예: "004")
        private String bankName;        // 은행명 (예: "국민은행")
        private String accountNo;       // 계좌번호 (하이픈 유무 무관, 서버에서 정규화)
        private String holderName;      // 예금주 실명
        private Long initialBalance;    // 초기 세팅 잔액 (미입력 시 기본 1,000,000원)
        private String sourceType;      // "CODEF" 또는 "MANUAL"
    }

    /**
     * 2. 토이 프로젝트 시연용 계좌 잔액 강제 변경 요청 DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BalanceUpdateRequest {
        private String bankName;
        private String accountNo;
        private Long newBalanceAmt;     // 변경할 목표 잔액 (원)
    }

    /**
     * 3. 계좌 존재 확인 및 사용자 실명 인증 요청 DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RealNameVerifyRequest {
        private Long memberId;          // (선택) MEMBER 테이블의 실명과도 교차 검증할 때 사용
        private String bankName;        // 은행명 (예: "국민은행")
        private String accountNo;       // 계좌번호 (예: "110-123-456789")
        private String holderName;      // 사용자가 입력한 실명 (예: "이모아")
    }

    /**
     * 4. 실명 인증 결과 응답 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RealNameVerifyResponse {
        private boolean accountExists;  // 실제 존재하는 계좌인지 여부
        private boolean realNameMatched;// 예금주 실명 일치 여부
        private String rspCode;         // 금결원 표준 응답코드 ("A0000": 정상, "A0001": 계좌없음, "A0003": 실명불일치)
        private String rspMessage;      // 응답 메시지
        private String fintechUseNum;   // 핀테크이용번호
        private String bankName;
        private String accountNoLast4;  // 뒤 4자리
        private String holderName;      // 예금주명
        private Long currentBalanceAmt; // 현재 잔액
    }

    /**
     * 5. 모임장 계좌 전용 회비 이체 요청 DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransferToLeaderRequest {
        private Long groupId;           // 모임 ID (서버가 이 ID로 GYE_ACCOUNT의 모임장 계좌를 찾음)
        private Long memberId;          // 송금하는 모임원 ID
        private String senderBankName;  // 출금 은행명
        private String senderAccountNo; // 출금 계좌번호
        private String senderHolderName;// 출금 계좌 예금주 실명
        private Long amount;            // 납입 금액 (원)
        private String payMonth;        // 납입 귀속 연월 (예: "202611")
        private Integer roundNo;        // 납입 회차 (예: 1)
    }

    /**
     * 6. 모임장 계좌 이체 완료 응답 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransferToLeaderResponse {
        private String rspCode;             // "A0000"
        private String rspMessage;          // "모임장 계좌로 이체가 완료되었습니다. (증빙 이중 확인 대기중)"
        private String bankTranId;          // 오픈뱅킹 거래고유번호
        private Long paymentProofId;        // 생성/갱신된 납입증빙 PK
        private String proofStatus;         // "PENDING" (확인중 - 증빙 이중 확인용)
        private String proofStatusKor;      // "확인중"
        private Long groupId;
        private Long senderMemberId;
        private String senderName;          // 납입내역 표시용 송금자 이름
        private String receiverBankName;    // 모임장 은행명
        private String receiverAccountLast4;// 모임장 계좌 뒤 4자리
        private String receiverHolderName;  // 모임장 예금주명
        private Long tranAmt;               // 이체 금액
        private Long senderAfterBalanceAmt; // 이체 후 내 잔액
        private String tranDateTime;        // 거래 일시
    }

    /**
     * 7. 모임 실시간 납입/거래내역 조회 응답 DTO (화면 거래내역 탭 표시용)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupPaymentHistoryItem {
        private Long paymentProofId;
        private Long tranId;
        private String bankTranId;
        private Long groupId;
        private Long memberId;
        private String memberName;          // 입금한 모임원 이름 (공통 마스킹 유틸이 처리)
        private String payMonth;            // YYYYMM
        private Integer roundNo;            // 회차
        private Long amount;                // 납입액
        private String status;              // DB 상태값: "PENDING"(확인중) / "CONFIRMED"(납입확인) / "REVISION"(보완요청)
        private String statusKor;           // 화면 표시용 한글 상태: "확인중" / "납입완료" / "보완요청"
        private String printContent;        // 통장 적요
        private String payDate;             // 거래 일시 (YYYY-MM-DD HH24:MI:SS)
        private String refNo;               // 관리자 참조번호 (CONFIRMED 시 부여)
    }

    /**
     * [내부 매핑용] DB 조회용 VO
     */
    @Data
    public static class MockAccountVO {
        private Long mockAccountId;
        private Long memberId;
        private String fintechUseNum;
        private String bankCode;
        private String bankName;
        private String accountNo;
        private String holderName;
        private Long balanceAmt;
        private String accountState;
    }

    @Data
    public static class EncryptedGyeAccountVO {
        private Long gyeAccountId;
        private Long groupId;
        private String bankName;
        private String holderNameEnc;
        private String accountNoEnc;
        private String accountNoLast4;
        private String accountNoHash;
        private Integer keyVer;
        private String verifyStatus;
        private String accountStatus;
    }

    /**
     * 8. 실제 계좌 동기화 시작 시점의 스냅샷 저장용 DTO
     * - 실 계좌와 Moava DB 간 괴리감을 줄이기 위한 기준값 저장
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountSnapshotRequest {
        private Long memberId;
        private String bankName;
        private String accountNo;
        private String holderName;
        private Long balanceAmt;
        private Long availableBalanceAmt;
        private String accountType;
        private String syncSource;
        private String syncReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountSnapshotRecord {
        private Long snapshotId;
        private Long memberId;
        private String bankName;
        private String accountNo;
        private String holderName;
        private Long balanceAmt;
        private Long availableBalanceAmt;
        private String accountType;
        private String syncSource;
        private String syncReason;
        private String syncedAt;
    }

    /**
     * 9. 실제 계좌 거래내역 증분 동기화용 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountTransactionSyncRequest {
        private Long memberId;
        private String bankName;
        private String accountNo;
        private String fromDate;
        private String toDate;
        private String pageNo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountTransactionRecord {
        private Long transactionId;
        private Long memberId;
        private String bankName;
        private String accountNo;
        private String transactionDate;
        private String inOutType;
        private Long amount;
        private Long balanceAfter;
        private String counterpartyName;
        private String printContent;
        private String sourceType;
    }

    /**
     * 10. ML 교육용 요약 레코드
     * - 정산 로직과 교육용 데이터셋이 섞이지 않도록 분리
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MlTrainingRecord {
        private Long memberId;
        private String bankName;
        private String accountNo;
        private String transactionDate;
        private String inOutType;
        private Long amount;
        private Long balanceAfter;
        private String printContent;
        private String featureGroup;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodefRegistrationStatusRequest {
        private Long memberId;
        private String bankCode;
        private String userId;
        private String userPw;
        private String clientName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodefRegistrationStatusResponse {
        private boolean registered;
        private String status;
        private String message;
        private String bankCode;
        private String accountNo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodefAccountListRequest {
        private Long memberId;
        private String bankCode;
        private String userId;
        private String userPw;
        private String clientName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodefAccountSummary {
        private String bankCode;
        private String bankName;
        private String accountNo;
        private String accountName;
        private String accountType;
        private String fintechUseNum;
        private boolean isPrimary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodefTransactionListRequest {
        private Long memberId;
        private String bankCode;
        private String accountNo;
        private String fintechUseNum;
        private String fromDate;
        private String toDate;
        private String pageNo;
        private String userId;
        private String userPw;
        private String clientName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodefTransactionItem {
        private String transactionDate;
        private String transactionTime;
        private String inOutType;
        private Long amount;
        private Long balanceAfter;
        private String counterpartyName;
        private String printContent;
        private String transactionId;
    }
}