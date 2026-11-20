package com.moava.group.procedures.dto;

import java.text.SimpleDateFormat;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;


public class DissolutionDTO {

    // 1. [신청] 모임장 해산 신청 폼
    @Getter
    @Setter
    public static class RequestForm {
        private Long leaderId;
        private Long userId;     // 프론트 호환용
        private String reason;   // 해산 사유

        public Long getEffectiveLeaderId() {
            return leaderId != null ? leaderId : userId;
        }

    }

    // 2. [승인 / 반려] ADMIN 처리 폼
    @Getter
    @Setter
    public static class AdminDecisionForm {
        private Long adminId;
        private String rejectReason; // 반려 시 사유 (AdminPages.jsx의 prompt('반려 사유') 호환)

        public Long getEffectiveAdminId() {
            return adminId;
        }

    }

    // 3. 해산 신청 상태 및 관리자 해산 목록 응답 DTO (AdminPages.jsx 호환)
    @Getter
    @Setter
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
            return ("APPROVED".equals(status) && decidedAt != null)
                    ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(decidedAt)
                    : null;
        }

    }
}