package com.moava.admin.sanctions.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * F39 (A03) 플랫폼 가입 제한 설정·해제 및 경고 현황 DTO.
 * - 플랫폼 가입 제한(BAN)·해제(BAN_RELEASE)는 SANCTION.ISSUED_ADMIN_ID로 기록
 * - 모임 경고(WARN)·강제 탈퇴(EXPEL)는 SANCTION.ISSUED_MEMBER_ID로 기록 (v3.1)
 */
public class AdminSanctionDTO {

    /** 플랫폼 가입 제한 설정 요청 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BanRequest {
        private Long memberId;
        private String reason;
    }

    /** 플랫폼 가입 제한 해제 요청 (사유 선택, 미입력 시 기본 문구 기록) */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BanReleaseRequest {
        private String reason;
    }

    /** 제재/해제 처리 대상 회원 DB 조회용 행 (FOR UPDATE 잠금) */
    @Getter
    @Setter
    public static class MemberBanTargetRow {
        private Long memberId;
        private String name;
        private String email;
        private String status;
        private String bannedYn;
    }

    /** 제재 대상 후보 회원 DB 조회용 행 (ACTIVE & BANNED_YN = 'N') */
    @Getter
    @Setter
    public static class BanCandidateRow {
        private Long memberId;
        private String name;
        private String email;
        private String regionSido;
        private String regionSigungu;
        private String createdAt;
    }

    /** 제재 대상 후보 회원 응답 항목 */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BanCandidateItem {
        private Long memberId;
        private String memberName;
        private String email;
        private String regionSido;
        private String regionSigungu;
        private String createdAt;
    }

    /** 현재 가입 제한 회원 DB 조회용 행 (BANNED_YN = 'Y' + 최신 BAN 이력) */
    @Getter
    @Setter
    public static class BannedMemberRow {
        private Long memberId;
        private String name;
        private String email;
        private Long latestSanctionId;
        private String reason;
        private Long issuedAdminId;
        private String issuedAdminName;
        private String bannedAt;
    }

    /** 현재 가입 제한 회원 응답 항목 */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BannedMemberItem {
        private Long memberId;
        private String memberName;
        private String email;
        private Long latestSanctionId;
        private String reason;
        private Long issuedAdminId;
        private String issuedAdminLabel;
        private String bannedAt;
    }

    /** 플랫폼 가입 제한 설정/해제 처리 응답 */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BanActionResponse {
        private Long sanctionId;
        private Long memberId;
        private String memberName;
        private String sanctionType; // BAN | BAN_RELEASE
        private boolean banned;
        private String reason;
        private Long issuedAdminId;
        private String message;
    }

    /** 모임별 회원 경고 현황 DB 조회용 행 */
    @Getter
    @Setter
    public static class MemberWarningRow {
        private Long gyeMemberId;
        private Long groupId;
        private String groupTitle;
        private String groupStatus;
        private Long memberId;
        private String memberName;
        private int warningCount;
        private String joinedAt;
    }

    /** 모임별 회원 경고 현황 응답 항목 (n/2회, 2회 = 강제 탈퇴 검토 대상) */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberWarningItem {
        private Long gyeMemberId;
        private Long groupId;
        private String groupTitle;
        private String groupStatus;
        private Long memberId;
        private String memberName;
        private int warningCount;
        private int maxWarnings;
        private boolean expelReviewTarget;
        private String statusText;
        private String joinedAt;
    }

    /** 경고·강제 탈퇴 이력 검색 조건 (MyBatis 파라미터) */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SanctionHistorySearchCriteria {
        private String escapedKeyword;
        private String sanctionType; // null(전체 WARN+EXPEL) | WARN | EXPEL
        private int offset;
        private int size;
    }

    /** 경고·강제 탈퇴 이력 DB 조회용 행 */
    @Getter
    @Setter
    public static class SanctionHistoryRow {
        private Long sanctionId;
        private String sanctionType;
        private Long groupId;
        private String groupTitle;
        private Long memberId;
        private String memberName;
        private Long issuedMemberId;
        private String issuedMemberName;
        private Long issuedAdminId;
        private String issuedAdminName;
        private String reason;
        private String createdAt;
    }

    /** 경고·강제 탈퇴 이력 응답 항목 */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SanctionHistoryItem {
        private Long sanctionId;
        private String sanctionType;      // WARN | EXPEL
        private String sanctionTypeLabel; // 경고 | 강제 탈퇴
        private Long groupId;
        private String groupTitle;
        private Long memberId;
        private String memberName;
        private String issuerType;        // MEMBER | ADMIN
        private Long issuedMemberId;
        private Long issuedAdminId;
        private String issuerLabel;
        private String reason;
        private String createdAt;
    }

    /** 경고·강제 탈퇴 이력 적용 필터 */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppliedHistoryFilters {
        private String keyword;
        private String sanctionType; // ALL | WARN | EXPEL
    }

    /** 경고·강제 탈퇴 이력 페이징 응답 (기본 8건) */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SanctionHistoryPageResponse {
        private List<SanctionHistoryItem> items;
        private int page;
        private int size;
        private long totalCount;
        private int totalPages;
        private boolean hasPrev;
        private boolean hasNext;
        private AppliedHistoryFilters filters;
    }

    /** A03 화면 초기 로딩용 종합 응답 (제재 후보 + 현재 제한 목록 + 모임별 경고 현황) */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SanctionOverviewResponse {
        private List<BanCandidateItem> candidates;
        private List<BannedMemberItem> bannedMembers;
        private int bannedCount;
        private List<MemberWarningItem> warnings;
    }
}
