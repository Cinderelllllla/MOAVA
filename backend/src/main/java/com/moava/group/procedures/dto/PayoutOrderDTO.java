package com.moava.group.procedures.dto;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

public class PayoutOrderDTO {

    @Getter
    @Setter
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
    }

    @Getter
    @Setter
    public static class ConsentForm {
        private Long memberId;
        private Long userId;
        private String agreedYn; // 기본 'Y'

        public Long getEffectiveMemberId() {
            return memberId != null ? memberId : userId;
        }

        public String getAgreedYn() { return agreedYn != null ? agreedYn : "Y"; }
    }

    @Getter
    @Setter
    public static class LeaderActionForm {
        private Long leaderId;
        private Long userId;

        public Long getEffectiveLeaderId() {
            return leaderId != null ? leaderId : userId;
        }
    }

    @Getter
    @Setter
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
    }
}