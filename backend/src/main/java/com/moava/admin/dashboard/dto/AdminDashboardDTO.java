package com.moava.admin.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;

public class AdminDashboardDTO {

    @Getter
    @Builder
    public static class DashboardResponse {
        private SummaryStats stats;
        private List<RecentAuditItem> recentLogs; // 최근 7건
        private boolean demoEnabled;              // 운영(prod) 프로필 시 false
    }

    @Getter
    @Setter
    public static class SummaryStats {
        private long totalMembers;           // 전체 회원 (ADMIN 제외, ACTIVE 기준)
        private long totalGroups;            // 전체 계모임
        private long pendingDissolutions;    // 해산 승인 대기 (PENDING)
        private long bannedMembers;          // 가입 제한 인원 (BANNED_YN = 'Y')
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentAuditItem {
        private Long auditLogId;
        private Long groupId;
        private String groupTitle;
        private String eventType;
        private String actorLabel;           // 회원 마스킹 / 관리자명 / 시스템
        private String description;
        private String createdAt;            // YYYY-MM-DD HH24:MI
    }

    @Getter
    @Setter
    public static class RecentAuditRow {
        private Long auditLogId;
        private Long groupId;
        private String groupTitle;
        private String eventType;
        private Long actorAdminId;
        private String adminName;
        private Long actorMemberId;
        private String memberName;
        private String description;
        private String createdAt;
    }

    @Getter
    @Builder
    public static class TestMembersResponse {
        private int createdCount;
        private List<String> createdEmails;
        private String message;
    }
}