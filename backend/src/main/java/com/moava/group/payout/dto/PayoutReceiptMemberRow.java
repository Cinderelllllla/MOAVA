package com.moava.group.payout.dto;

/**
 * F28 모임 참여 회원별 곗돈 수령 완료 현황 조회 행 (DB → Service 내부용).
 */
public record PayoutReceiptMemberRow(
        long memberId,
        String memberName,
        String role,
        Integer confirmedRoundNo,
        String payoutReceivedYn,
        String payoutReceivedAt
) {
    public boolean payoutReceived() {
        return "Y".equalsIgnoreCase(payoutReceivedYn);
    }
}
