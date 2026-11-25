package com.moava.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * F40 (S15 · A08) 고객센터 신고 접수 및 처리 DTO.
 * - 회원(S15): 대상 모임(선택), 신고 유형, 상세 내용(AES-256-GCM 암호화 저장)
 * - 관리자(A08): 전체 조회 + 상태 필터 + 상태 변경(처리 메모 필수) + 처리 이력(AUDIT_LOG REPORT_STATUS)
 */
public class SupportReportDTO {

    /** 회원 신고 접수 요청 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateReportRequest {
        private Long groupId;
        private String category;
        private String detail;
    }

    /** 관리자 신고 상태 변경 요청 (처리 메모 필수) */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusUpdateRequest {
        private String status;
        private String adminMemo;
    }

    /** 회원 참여/운영 모임 조회용 DB 행 */
    @Getter
    @Setter
    public static class MemberGroupOptionRow {
        private Long groupId;
        private String title;
        private String status;
        private String role;
    }

    /** 대상 모임 검증용 DB 행 */
    @Getter
    @Setter
    public static class GroupBasicRow {
        private Long groupId;
        private String title;
        private String status;
    }

    /** SUPPORT_REPORT 조회용 DB 행 (DETAIL_ENC는 암호문 CLOB 상태) */
    @Getter
    @Setter
    public static class SupportReportRow {
        private Long supportReportId;
        private Long memberId;
        private String reporterName;
        private Long groupId;
        private String groupTitle;
        private String category;
        private String detailEnc;
        private Integer keyVer;
        private String status;
        private String adminMemo;
        private Long handledBy;
        private String handledByName;
        private String createdAt;
        private String updatedAt;
    }

    /** AUDIT_LOG(REPORT_STATUS) 처리 이력 조회용 DB 행 */
    @Getter
    @Setter
    public static class StatusHistoryRow {
        private Long auditLogId;
        private Long actorAdminId;
        private String actorAdminName;
        private String description;
        private String createdAt;
    }

    /** 상태별 건수 집계용 DB 행 */
    @Getter
    @Setter
    public static class StatusCountRow {
        private String status;
        private Integer count;
    }

    /** 회원 신고 대상 모임 선택 항목 */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupOptionItem {
        private Long groupId;
        private String title;
        private String status;
        private String role;
    }

    /** 신고 상태 변경 이력 항목 */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusHistoryItem {
        private Long auditLogId;
        private String fromStatusLabel;
        private String toStatusLabel;
        private String memo;
        private String rawDescription;
        private Long handledBy;
        private String handledByLabel;
        private String createdAt;
    }

    /** 신고 항목 응답 (복호화된 상세는 신고자 본인·ADMIN 응답 시에만 포함) */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportItem {
        private Long reportId;
        private Long memberId;
        private String reporterName;
        private Long groupId;
        private String groupTitle;
        private String category;
        private String categoryLabel;
        private String detail;
        private String status;
        private String statusLabel;
        private String adminMemo;
        private Long handledBy;
        private String handledByLabel;
        private String createdAt;
        private String updatedAt;
        private List<StatusHistoryItem> history;
    }

    /** 회원 신고 접수 응답 */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateReportResponse {
        private ReportItem report;
        private String message;
        private String guidanceNotice;
    }

    /** 회원 나의 신고 목록 응답 */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberReportListResponse {
        private List<ReportItem> items;
        private int totalCount;
        private String guidanceNotice;
    }

    /** 관리자 전체 신고 목록 응답 */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminReportListResponse {
        private List<ReportItem> items;
        private int totalCount;
        private String statusFilter;
        private Map<String, Integer> statusCounts;
        private String guidanceNotice;
    }

    /** 관리자 신고 상태 변경 응답 */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusUpdateResponse {
        private ReportItem report;
        private String previousStatus;
        private String previousStatusLabel;
        private String currentStatus;
        private String currentStatusLabel;
        private String message;
    }
}
