package com.moava.group.payout.dto;

/**
 * F28 화면 표시용 회원별 곗돈 수령 완료 항목.
 */
public record PayoutReceiptMemberItem(
        long memberId,
        String memberName,
        String role,
        Integer confirmedRoundNo,
        boolean payoutReceived,
        String payoutReceivedAt,
        boolean self
) {
}
