package com.moava.group.procedures.dto;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DissolutionDTO {

    // 1. [신청] 모임장 해산 신청 폼
    public static class RequestForm {
        private Long leaderId;
        private Long userId;     // 프론트 호환용
        private String reason;   // 해산 사유

        public Long getEffectiveLeaderId() {
            return leaderId != null ? leaderId : userId;
        }

        public Long getLeaderId() { return leaderId; }
        public void setLeaderId(Long leaderId) { this.leaderId = leaderId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    // 2. [승인 / 반려] ADMIN 처리 폼
    public static class AdminDecisionForm {
        private Long adminId;
        private String rejectReason; // 반려 시 사유 (AdminPages.jsx의 prompt('반려 사유') 호환)

        public Long getEffectiveAdminId() {
            return adminId;
        }

        public Long getAdminId() { return adminId; }
        public void setAdminId(Long adminId) { this.adminId = adminId; }
        public String getRejectReason() { return rejectReason; }
        public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    }

    // 3. 해산 신청 상태 및 관리자 해산 목록 응답 DTO (AdminPages.jsx 호환)
    public static class StatusResponse {
        private Long dissolutionRequestId;
        private Long groupId;
        private String groupTitle;
        private String groupStatus;         // ACTIVE, DISSOLVE_PENDING, DISSOLVED
        private Long requestedBy;
        private String leaderName;
        private String reason;
        private String status;              // PENDING, APPROVED, REJECTED
        private Long decidedBy;
        private Date decidedAt;
        private Date createdAt;

        // AdminDissolve 화면 표시용 집계 필드
        private Integer memberCount;
        private Integer unconfirmedProofCount; // 미처리 증빙 건수 (STATUS != 'CONFIRMED')
        private Integer activeWithdrawalCount; // 진행 중 탈퇴 건수
        private Long totalConfirmedAmount;     // 총 납입 확인 금액
        private Long perMemberRefundAmount;    // 1/N 정산 예상 금액

        // AdminPages.jsx 호환용 별칭 Getter
        public Long getId() { return groupId; }
        public String getTitle() { return groupTitle; }
        public String getStatusBadge() {
            if ("PENDING".equals(status)) return "관리자 검토 대기";
            if ("APPROVED".equals(status)) return "승인 완료";
            if ("REJECTED".equals(status)) return "반려";
            return "운영 중";
        }
        public String getRequestedAt() {
            return createdAt != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(createdAt) : null;
        }
        public String getApprovedAt() {
            return decidedAt != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(decidedAt) : null;
        }

        public Long getDissolutionRequestId() { return dissolutionRequestId; }
        public void setDissolutionRequestId(Long dissolutionRequestId) { this.dissolutionRequestId = dissolutionRequestId; }
        public Long getGroupId() { return groupId; }
        public void setGroupId(Long groupId) { this.groupId = groupId; }
        public String getGroupTitle() { return groupTitle; }
        public void setGroupTitle(String groupTitle) { this.groupTitle = groupTitle; }
        public String getGroupStatus() { return groupStatus; }
        public void setGroupStatus(String groupStatus) { this.groupStatus = groupStatus; }
        public Long getRequestedBy() { return requestedBy; }
        public void setRequestedBy(Long requestedBy) { this.requestedBy = requestedBy; }
        public String getLeaderName() { return leaderName; }
        public void setLeaderName(String leaderName) { this.leaderName = leaderName; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Long getDecidedBy() { return decidedBy; }
        public void setDecidedBy(Long decidedBy) { this.decidedBy = decidedBy; }
        public Date getDecidedAt() { return decidedAt; }
        public void setDecidedAt(Date decidedAt) { this.decidedAt = decidedAt; }
        public Date getCreatedAt() { return createdAt; }
        public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
        public Integer getMemberCount() { return memberCount; }
        public void setMemberCount(Integer memberCount) { this.memberCount = memberCount; }
        public Integer getUnconfirmedProofCount() { return unconfirmedProofCount; }
        public void setUnconfirmedProofCount(Integer unconfirmedProofCount) { this.unconfirmedProofCount = unconfirmedProofCount; }
        public Integer getActiveWithdrawalCount() { return activeWithdrawalCount; }
        public void setActiveWithdrawalCount(Integer activeWithdrawalCount) { this.activeWithdrawalCount = activeWithdrawalCount; }
        public Long getTotalConfirmedAmount() { return totalConfirmedAmount; }
        public void setTotalConfirmedAmount(Long totalConfirmedAmount) { this.totalConfirmedAmount = totalConfirmedAmount; }
        public Long getPerMemberRefundAmount() { return perMemberRefundAmount; }
        public void setPerMemberRefundAmount(Long perMemberRefundAmount) { this.perMemberRefundAmount = perMemberRefundAmount; }
    }
}