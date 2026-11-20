package com.moava.group.procedures.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class WithdrawalDTO {

    @Getter
    @Setter
    public static class ApplyForm {
        private Long memberId;
        private Long userId;
        private String reasonType; // 미전달 시 기본값 'PERSONAL'
        private String detail;
        private String reason;     // procedures.js 호환용
        private String specialYn;
        private Boolean special;   // procedures.js 호환용 (boolean)

        public Long getEffectiveMemberId() {
            return memberId != null ? memberId : userId;
        }

        public String getEffectiveReasonType() {
            return (reasonType != null && !reasonType.isBlank()) ? reasonType : "PERSONAL";
        }

        public String getEffectiveDetail() {
            return (detail != null && !detail.isBlank()) ? detail : reason;
        }

        public String getEffectiveSpecialYn() {
            if (special != null) return special ? "Y" : "N";
            return "Y".equalsIgnoreCase(specialYn) ? "Y" : "N";
        }
    }

    @Getter
    @Setter
    public static class SettlementForm {
        private Long leaderId;
        private Long userId;
        private Long settlementAmount;
        private Long amount;       // procedures.js 호환용
        private String settlementMemo;
        private String memo;       // procedures.js 호환용

        public Long getEffectiveLeaderId() {
            return leaderId != null ? leaderId : userId;
        }

        public Long getEffectiveAmount() {
            return settlementAmount != null ? settlementAmount : amount;
        }

        public String getEffectiveMemo() {
            return (settlementMemo != null && !settlementMemo.isBlank()) ? settlementMemo : memo;
        }
    }

    @Getter
    @Setter
    public static class ConsentForm {
        private Long memberId;
        private Long userId;
        private String agreedYn;

        public Long getEffectiveMemberId() {
            return memberId != null ? memberId : userId;
        }

        public String getAgreedYn() {
            return agreedYn;
        }
    }

    @Getter
    @Setter
    public static class ConsentExemptionForm {
        private String reason;
    }

    @Getter
    @Setter
    public static class ActionForm {
        private Long actorId;
        private Long userId;
        private Long memberId;
        private Long leaderId;

        public Long getEffectiveActorId() {
            if (actorId != null) return actorId;
            if (userId != null) return userId;
            if (memberId != null) return memberId;
            return leaderId;
        }
    }

    @Getter
    @Setter
    public static class StatusResponse {
        private Long withdrawalRequestId;
        private Long groupId;
        private Long memberId;
        private String memberName;
        private String reasonType;
        private String detail;
        private String specialYn;
        private Long settlementAmount;
        private String settlementMemo;
        private Long settlementBy;
        private Date settlementAt;
        private String status;
        private Integer reconfirmCount;
        private Integer agreedCount;
        private Integer exemptedCount;
        private Integer targetCount;
        private Date decidedAt;
        private Date createdAt;
        private List<Long> consents = new ArrayList<>();

        // procedures.js 호환용 Getter
        public Long getId() { return withdrawalRequestId; }
        public Long getUserId() { return memberId; }
        public String getReason() { return detail; }
        public boolean isSpecial() { return "Y".equalsIgnoreCase(specialYn); }
        public Integer getReconfirm() { return reconfirmCount; }
        public String getStatusLabel() {
            if ("SETTLEMENT_PENDING".equals(status)) return "정산안 작성 대기";
            if ("CONSENT_PENDING".equals(status)) return "정산 동의 대기";
            if ("APPROVED".equals(status)) return "완료";
            if ("REJECTED".equals(status)) return "반려";
            return status;
        }
    }

    @Getter
    @Setter
    public static class ConsentTargetResponse {
        private Long memberId;
        private String memberName;
        private String memberStatus;
        private String status;
        private Date respondedAt;
        private Long exemptedBy;
        private Date exemptedAt;
        private String exemptionReason;
    }
}
