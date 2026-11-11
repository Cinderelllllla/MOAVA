package com.moava.group.procedures.dto;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PayoutOrderDTO {

    public static class RequestForm {
        private Long memberId;
        private Long userId;     // procedures.js 호환용
        private Integer roundNo;
        private Integer round;   // procedures.js 호환용
        private String reason;

        public Long getEffectiveMemberId() {
            return memberId != null ? memberId : userId;
        }
        public Integer getEffectiveRoundNo() {
            return roundNo != null ? roundNo : round;
        }

        public Long getMemberId() { return memberId; }
        public void setMemberId(Long memberId) { this.memberId = memberId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Integer getRoundNo() { return roundNo; }
        public void setRoundNo(Integer roundNo) { this.roundNo = roundNo; }
        public Integer getRound() { return round; }
        public void setRound(Integer round) { this.round = round; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class ConsentForm {
        private Long memberId;
        private Long userId;
        private String agreedYn; // 기본 'Y'

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

    public static class LeaderActionForm {
        private Long leaderId;
        private Long userId;

        public Long getEffectiveLeaderId() {
            return leaderId != null ? leaderId : userId;
        }
        public Long getLeaderId() { return leaderId; }
        public void setLeaderId(Long leaderId) { this.leaderId = leaderId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
    }

    public static class StatusResponse {
        private Long payoutRequestId;
        private Long groupId;
        private Long memberId;
        private String memberName;
        private Integer roundNo;
        private String reason;
        private Integer aiSuggestedRound;
        private String status;           // DB 코드: REQUESTED, CONSENT_PENDING, CONFIRMED, REJECTED
        private Integer agreedCount;
        private Integer targetCount;
        private Date createdAt;
        private Date decidedAt;

        // procedures.js 화면 호환용 배열 (동의한 회원 ID 목록 / 동의 대상 회원 ID 목록)
        private List<Long> consents = new ArrayList<>();
        private List<Long> affected = new ArrayList<>();

        // 프론트엔드 procedures.js 필드명 별칭 Getter
        public Long getId() { return payoutRequestId; }
        public Long getUserId() { return memberId; }
        public Integer getRound() { return roundNo; }
        public String getStatusLabel() {
            if ("REQUESTED".equals(status)) return "신청";
            if ("CONSENT_PENDING".equals(status)) return "동의 요청";
            if ("CONFIRMED".equals(status)) return "확정";
            if ("REJECTED".equals(status)) return "반려";
            return status;
        }

        public Long getPayoutRequestId() { return payoutRequestId; }
        public void setPayoutRequestId(Long payoutRequestId) { this.payoutRequestId = payoutRequestId; }
        public Long getGroupId() { return groupId; }
        public void setGroupId(Long groupId) { this.groupId = groupId; }
        public Long getMemberId() { return memberId; }
        public void setMemberId(Long memberId) { this.memberId = memberId; }
        public String getMemberName() { return memberName; }
        public void setMemberName(String memberName) { this.memberName = memberName; }
        public Integer getRoundNo() { return roundNo; }
        public void setRoundNo(Integer roundNo) { this.roundNo = roundNo; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public Integer getAiSuggestedRound() { return aiSuggestedRound; }
        public void setAiSuggestedRound(Integer aiSuggestedRound) { this.aiSuggestedRound = aiSuggestedRound; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Integer getAgreedCount() { return agreedCount; }
        public void setAgreedCount(Integer agreedCount) { this.agreedCount = agreedCount; }
        public Integer getTargetCount() { return targetCount; }
        public void setTargetCount(Integer targetCount) { this.targetCount = targetCount; }
        public Date getCreatedAt() { return createdAt; }
        public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
        public Date getDecidedAt() { return decidedAt; }
        public void setDecidedAt(Date decidedAt) { this.decidedAt = decidedAt; }
        public List<Long> getConsents() { return consents; }
        public void setConsents(List<Long> consents) { this.consents = consents; }
        public List<Long> getAffected() { return affected; }
        public void setAffected(List<Long> affected) { this.affected = affected; }
    }
}