package com.moava.openbanking.dto;

import com.moava.openbanking.codef.CodefDto;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 프론트엔드 연동 및 CODEF ↔ 모의 은행(MOCK_KFTC_ACCOUNT / MOCK_KFTC_TRANSACTION) 적재용 DTO
 */
public class MockBankDto {

    /** CODEF 실계좌인증 결과를 모의 은행 원장에 적재한 결과 요약 */
    @Getter @Setter
    public static class ProvisionResult {
        private String fintechUseNum;
        private String userSeqNo;
        private String bankCode;
        private String bankName;
        private String accountMasked;
        private String accountLast4;
        private String holderName;
        private Long balanceAmt;
        private Long realBalanceAmt;
        private String leaderYn;
        private boolean leaderAccount;
        private boolean created;
        private int syncedTransactionCount;
        private int skippedDuplicateCount;
        private boolean openingSnapshotCreated;
        private String status;
        private LocalDateTime syncedAt;
    }

    /** 프론트 표시용 내 모의 은행 계좌 정보 */
    @Getter @Setter
    public static class MockAccountView {
        private String fintechUseNum;
        private String bankCode;
        private String bankName;
        private String accountMasked;
        private String accountLast4;
        private String holderName;
        private String userSeqNo;
        private Long balanceAmt;
        private boolean leaderAccount;
        private String leaderYn;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    /** 프론트 표시용 모의 은행 거래내역 항목 */
    @Getter @Setter
    public static class MockTransactionView {
        private Long txId;
        private String bankTranId;
        private String fintechUseNum;
        private String bankName;
        private String accountMasked;
        private String tranDate;
        private String tranTime;
        private LocalDateTime tranAt;
        private String inoutType;
        private Long tranAmt;
        private Long afterBalanceAmt;
        private String printContent;
        private String counterpartyName;
        private String branchName;
    }

    /** 프론트 차트/금융요약용 월별 입출금 집계 */
    @Getter @Setter
    public static class MonthlyAggregate {
        private String yearMonth;
        private Long totalDepositAmt;
        private Long totalWithdrawAmt;
        private Long netAmt;
        private int txCount;
    }

    /** 회비 이체 가능한 모임장 수취 계좌 (프론트 드롭다운/카드 표시용) */
    @Getter @Setter
    public static class LeaderTargetView {
        private String fintechUseNum;
        private String bankCode;
        private String bankName;
        private String accountMasked;
        private String holderNameMasked;
        private Long groupId;
        private String groupTitle;
        private Long monthlyDuesAmt;
        private boolean myGroup;
    }

    /** 내부 MyBatis 조회용: 회원이 참여 중인 활성 모임 정보 */
    @Getter @Setter
    public static class MemberGroupInfo {
        private Long groupId;
        private String title;
        private Long ownerId;
        private Long amount;
        private Integer payDay;
        private String status;
    }

    /** 프론트 오픈뱅킹 화면 원샷 초기 로딩 응답 (GET /api/openbanking/accounts/overview) */
    @Getter @Setter
    public static class OverviewResponse {
        private Long memberId;
        private String memberName;
        private String userSeqNo;
        private int codefVerifiedCount;
        private int mockAccountCount;
        private Long totalBalanceAmt;
        private List<CodefDto.ConnectionSummary> connections;
        private List<MockAccountView> accounts;
        private List<MockTransactionView> recentTransactions;
        private List<MonthlyAggregate> monthlySummary;
        private List<LeaderTargetView> leaderTargets;
    }

    /** 특정 모의 계좌 거래내역 및 집계 응답 */
    @Getter @Setter
    public static class AccountTransactionsResponse {
        private MockAccountView account;
        private String fromDate;
        private String toDate;
        private String inquiryType;
        private String sortOrder;
        private int count;
        private Long totalDepositAmt;
        private Long totalWithdrawAmt;
        private List<MonthlyAggregate> monthlySummary;
        private List<MockTransactionView> transactions;
    }

    /** 이미 인증된 CODEF 실계좌를 모의 은행에 (재)적재/동기화 요청 */
    @Getter @Setter
    public static class SyncFromCodefRequest {
        private String organization;        // 4자리 CODEF 코드, 3자리 KFTC 코드, 또는 은행명
        private Boolean refreshFromCodef;   // true: CODEF 실거래 재조회 후 적재 / false(기본): 저장된 인증정보로 적재
        private String accountPassword;     // 선택: refreshFromCodef=true 시 은행별 조건부
        private String birthDate;           // 선택: YYMMDD
        private String startDate;           // 선택: yyyyMMdd
        private String endDate;             // 선택: yyyyMMdd
        private Long initialMockBalance;    // 선택: 모의 은행 반영 잔액 직접 지정 (미지정 시 실잔액 또는 기존 잔액 유지)
        private Boolean leaderAccount;      // 선택: 모임장 수취 계좌 여부 (미지정 시 GYE_GROUP.OWNER_ID 자동 판별)
    }

    /** 모임장 수취 계좌 여부 변경 요청 */
    @Getter @Setter
    public static class UpdateLeaderRequest {
        private Boolean leader;
    }

    /** 시연용 모의 자금 충전 요청 */
    @Getter @Setter
    public static class DemoDepositRequest {
        private Long amount;
        private String printContent;
    }

    /** 시연용 모의 자금 충전 응답 */
    @Getter @Setter
    public static class DemoDepositResponse {
        private String fintechUseNum;
        private String bankTranId;
        private Long depositAmt;
        private Long afterBalanceAmt;
        private String printContent;
        private LocalDateTime tranAt;
    }

    /** 프론트에서 호출하는 모의 회비 이체 요청 (내 모의계좌 -> 모임장 수취 계좌) */
    @Getter @Setter
    public static class MemberTransferRequest {
        private String fromFintechUseNum;
        private String toFintechUseNum;
        private Long amount;
        private String wdPrintContent;
        private String dpsPrintContent;
        private String bankTranId;
    }

    /** 프론트 표시용 모의 회비 이체 응답 */
    @Getter @Setter
    public static class MemberTransferResponse {
        private String bankTranId;
        private String bankTranDate;
        private String fromFintechUseNum;
        private String fromBankName;
        private String fromHolderName;
        private Long fromAfterBalanceAmt;
        private String toFintechUseNum;
        private String toBankName;
        private String toHolderNameMasked;
        private Long tranAmt;
        private String printContent;
        private String message;
    }
}
