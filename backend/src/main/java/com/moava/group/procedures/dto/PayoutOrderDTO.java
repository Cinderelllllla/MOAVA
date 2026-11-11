package com.moava.group.procedures.dto;

import lombok.Data;
import java.util.Date;

public class PayoutOrderDTO {

    // 1. 수령 순서 신청/수정 요청 DTO
    @Data
    public static class RequestForm {
        private Long memberId;
        private Integer roundNo; // 희망 회차 (1 ~ 최대 인원)
        private String reason;   // 신청 사유
    }

    // 2. 모임원 개별 동의 요청 DTO
    @Data
    public static class ConsentForm {
        private Long memberId;
        private String agreedYn; // 'Y' 또는 'N'
    }

    // 3. 모임장 액션(동의 요청 / 최종 확정) 요청 DTO
    @Data
    public static class LeaderActionForm {
        private Long leaderId;
    }

    // 4. 신청·동의 현황 목록 응답 DTO (화면 표시용)
    @Data
    public static class StatusResponse {
        private Long payoutRequestId;
        private Long groupId;
        private Long memberId;
        private String memberName;
        private Integer roundNo;
        private String reason;
        private Integer aiSuggestedRound; // AI 제안 회차 (연체 확률 내림차순)
        private String status;            // REQUESTED, CONSENT_PENDING, CONFIRMED, REJECTED
        private Integer agreedCount;      // 현재 동의('Y') 인원수
        private Integer targetCount;      // 동의 대상 전체 인원수 (신청자 제외 ACTIVE 회원)
        private Date createdAt;
        private Date decidedAt;
    }
}