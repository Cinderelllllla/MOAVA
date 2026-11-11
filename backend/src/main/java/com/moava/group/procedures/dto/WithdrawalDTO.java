package com.moava.group.procedures.dto;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class WithdrawalDTO {

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

        public Long getMemberId() { return memberId; }
        public void setMemberId(Long memberId) { this.memberId = memberId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getReasonType() { return reasonType; }
        public void setReasonType(String reasonType) { this.reasonType = reasonType; }
        public String getDetail() { return detail; }
        public void setDetail(String detail) { this.detail = detail; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getSpecialYn() { return specialYn; }
        public void setSpecialYn(String specialYn) { this.specialYn = specialYn; }
        public Boolean getSpecial() { return special; }
        public void setSpecial(Boolean special) { this.special = special; }
    }

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
            Long raw = settlementAmount != null ? settlementAmount : amount;
            if (raw == null) return null;
            // 프론트에서 '50'(50만 원)처럼 만 원 단위로 보낸 경우 DB(KRW 원 단위)에 맞게 * 10000 변환
            if (raw != 0 && Math.abs(raw) < 10000) {
                return raw * 10000L;
            }
            return raw;
        }
        public String getEffectiveMemo() {
            String m = (settlementMemo != null && !settlementMemo.isBlank()) ? settlementMemo : memo;
            return (m != null && !m.isBlank()) ? m : "정산 산출 내역";
        }

        public Long getLeaderId() { return leaderId; }
        public void setLeaderId(Long leaderId) { this.leaderId = leaderId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Long getSettlementAmount() { return settlementAmount; }
        public void setSettlementAmount(Long settlementAmount) { this.settlementAmount = settlementAmount; }
        public Long getAmount() { return amount; }
        public void setAmount(Long amount) { this.amount = amount; }
        public String getSettlementMemo() { return settlementMemo; }
        public void setSettlementMemo(String settlementMemo) { this.settlementMemo = settlementMemo; }
        public String getMemo() { return memo; }
        public void setMemo(String memo) { this.memo = memo; }
    }

    public static class ConsentForm {
        private Long memberId;
        private Long userId;
        private String agreedYn;

        public Long getEffectiveMemberId() {
            return memberId != null ? memberId : userId;
        }
        public Long getMemberId() { return memberId; }
        public void setMemberId(Long memberId) { this.memberId = memberId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getAgreedYn() { return agreedYn != null ? agreedYn : "Y"; }
        public void setAgreedYn(String agreedYn) { this.agreedYn = agreedYn; }
    }

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
        public Long getActorId() { return actorId; }
        public void setActorId(Long actorId) { this.actorId = actorId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Long getMemberId() { return memberId; }
        public void setMemberId(Long memberId) { this.memberId = memberId; }
        public Long getLeaderId() { return leaderId; }
        public void setLeaderId(Long leaderId) { this.leaderId = leaderId; }
    }

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

        public Long getWithdrawalRequestId() { return withdrawalRequestId; }
        public void setWithdrawalRequestId(Long withdrawalRequestId) { this.withdrawalRequestId = withdrawalRequestId; }
        public Long getGroupId() { return groupId; }
        public void setGroupId(Long groupId) { this.groupId = groupId; }
        public Long getMemberId() { return memberId; }
        public void setMemberId(Long memberId) { this.memberId = memberId; }
        public String getMemberName() { return memberName; }
        public void setMemberName(String memberName) { this.memberName = memberName; }
        public String getReasonType() { return reasonType; }
        public void setReasonType(String reasonType) { this.reasonType = reasonType; }
        public String getDetail() { return detail; }
        public void setDetail(String detail) { this.detail = detail; }
        public String getSpecialYn() { return specialYn; }
        public void setSpecialYn(String specialYn) { this.specialYn = specialYn; }
        public Long getSettlementAmount() { return settlementAmount; }
        public void setSettlementAmount(Long settlementAmount) { this.settlementAmount = settlementAmount; }
        public String getSettlementMemo() { return settlementMemo; }
        public void setSettlementMemo(String settlementMemo) { this.settlementMemo = settlementMemo; }
        public Long getSettlementBy() { return settlementBy; }
        public void setSettlementBy(Long settlementBy) { this.settlementBy = settlementBy; }
        public Date getSettlementAt() { return settlementAt; }
        public void setSettlementAt(Date settlementAt) { this.settlementAt = settlementAt; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Integer getReconfirmCount() { return reconfirmCount; }
        public void setReconfirmCount(Integer reconfirmCount) { this.reconfirmCount = reconfirmCount; }
        public Integer getAgreedCount() { return agreedCount; }
        public void setAgreedCount(Integer agreedCount) { this.agreedCount = agreedCount; }
        public Integer getTargetCount() { return targetCount; }
        public void setTargetCount(Integer targetCount) { this.targetCount = targetCount; }
        public Date getDecidedAt() { return decidedAt; }
        public void setDecidedAt(Date decidedAt) { this.decidedAt = decidedAt; }
        public Date getCreatedAt() { return createdAt; }
        public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
        public List<Long> getConsents() { return consents; }
        public void setConsents(List<Long> consents) { this.consents = consents; }
    }
}