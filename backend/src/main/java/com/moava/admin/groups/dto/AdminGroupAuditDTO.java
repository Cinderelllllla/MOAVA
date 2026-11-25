package com.moava.admin.groups.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * F38 (A02) 관리자 계모임 관리 및 감사 이력 조회 DTO.
 * 가입 승인·납입 증빙 승인 개입 없이 조회·감사 전용 필드만 정의한다.
 */
public class AdminGroupAuditDTO {

    /** 모임 목록 행 (DB 조회용 원시 행 — 이름 마스킹 전) */
    @Getter
    @Setter
    public static class GroupSummaryRow {
        private Long groupId;
        private String title;
        private Long ownerId;
        private String ownerName;
        private String status;
        private String visibility;
        private int activeMemberCount;
        private int maxMembers;
        private int payDay;
        private long amount;
        private int months;
        private BigDecimal assumedRate;
        private String accountVerifyStatus;
        private String createdAt;
    }

    /** 모임 목록 응답 항목 */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupSummaryItem {
        private Long groupId;
        private String title;
        private Long ownerId;
        private String ownerName;
        private String status;
        private String visibility;
        private int activeMemberCount;
        private int maxMembers;
        private int payDay;
        private long amount;
        private int months;
        private BigDecimal assumedRate;
        private String accountVerifyStatus;
        private String createdAt;
    }

    /** 모임 상세 DB 조회용 원시 행 */
    @Getter
    @Setter
    public static class GroupDetailRow {
        private Long groupId;
        private String title;
        private String purpose;
        private String regionSido;
        private String regionSigungu;
        private Long ownerId;
        private String ownerName;
        private String status;
        private String visibility;
        private int activeMemberCount;
        private int maxMembers;
        private long amount;
        private int months;
        private int payDay;
        private BigDecimal assumedRate;
        private String overduePolicy;
        private String orderConfirmedYn;
        private String rulesTitle;
        private String rulesBody;
        private String description;
        private String startedAt;
        private String createdAt;
        private int joinRequestCount;
        private int pendingJoinRequestCount;
        private int paymentProofCount;
        private int pendingProofCount;
        private int withdrawalRequestCount;
        private int activeWithdrawalCount;
        private int overdueCaseCount;
        private int openOverdueCaseCount;
    }

    /** 모임 계좌 요약 DB 조회용 원시 행 (암호문 미조회, 뒤 4자리·등록자 이름만 사용) */
    @Getter
    @Setter
    public static class GroupAccountRow {
        private Long gyeAccountId;
        private String bankName;
        private String registeredByName;
        private String accountNoLast4;
        private String verifyStatus;
        private String accountStatus;
        private String requestedAt;
        private String verifiedAt;
    }

    /** 모임 계좌 요약 응답 (조회 전용) */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupAccountSummary {
        private Long gyeAccountId;
        private String bankName;
        private String maskedHolderName;
        private String maskedAccountNo;
        private String accountNoLast4;
        private String verifyStatus;
        private String accountStatus;
        private String requestedAt;
        private String verifiedAt;
    }

    /** 모임 관리자 상세 패널 응답 */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupDetailResponse {
        private Long groupId;
        private String title;
        private String purpose;
        private String regionSido;
        private String regionSigungu;
        private Long ownerId;
        private String ownerName;
        private String status;
        private String visibility;
        private int activeMemberCount;
        private int maxMembers;
        private long amount;
        private int months;
        private int payDay;
        private BigDecimal assumedRate;
        private String overduePolicy;
        private boolean orderConfirmed;
        private String rulesTitle;
        private String rulesBody;
        private String description;
        private String startedAt;
        private String createdAt;
        private int joinRequestCount;
        private int pendingJoinRequestCount;
        private int paymentProofCount;
        private int pendingProofCount;
        private int withdrawalRequestCount;
        private int activeWithdrawalCount;
        private int overdueCaseCount;
        private int openOverdueCaseCount;
        private GroupAccountSummary currentAccount;
        private GroupAccountSummary pendingAccount;
        private String readOnlyNotice;
    }

    /** 감사 이력 조회 조건 (MyBatis 파라미터) */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditLogSearchCriteria {
        private Long groupId;
        private String eventType;
        private String reviewStatus; // ALL / REVIEW / DONE
        private boolean adminOnly;
        private Long actorAdminId;
        private int offset;
        private int size;
    }

    /** 감사 이력 DB 조회용 원시 행 */
    @Getter
    @Setter
    public static class AuditLogRow {
        private Long auditLogId;
        private Long groupId;
        private String groupTitle;
        private String eventType;
        private Long actorAdminId;
        private String adminName;
        private Long actorMemberId;
        private String memberName;
        private Long targetMemberId;
        private String targetMemberName;
        private String description;
        private String createdAt;
    }

    /** 감사 이력 응답 항목 */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditLogItem {
        private Long auditLogId;
        private Long groupId;
        private String groupTitle;
        private String eventType;
        private String reviewCategory; // REVIEW (_REQUEST / _DETECT) | DONE
        private String actorType;      // ADMIN | MEMBER | SYSTEM
        private Long actorAdminId;
        private Long actorMemberId;
        private String actorLabel;
        private Long targetMemberId;
        private String targetMemberLabel;
        private String description;
        private String createdAt;
    }

    /** 적용된 필터 정보 */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppliedFilters {
        private Long groupId;
        private String eventType;
        private String reviewStatus;
        private boolean adminOnly;
        private Long actorAdminId;
    }

    /** 감사 이력 페이징 응답 (기본 페이지당 8건) */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditLogPageResponse {
        private List<AuditLogItem> items;
        private int page;
        private int size;
        private long totalCount;
        private int totalPages;
        private boolean hasPrev;
        private boolean hasNext;
        private AppliedFilters filters;
    }

    /** 담당 관리자 선택 옵션 (v3.1) */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminOption {
        private Long adminId;
        private String name;
        private String email;
        private String status;
    }

    /** 감사 이력 필터 메타 옵션 (이벤트 유형 + 담당 관리자 목록) */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditFilterOptionsResponse {
        private List<String> eventTypes;
        private List<AdminOption> admins;
    }
}
