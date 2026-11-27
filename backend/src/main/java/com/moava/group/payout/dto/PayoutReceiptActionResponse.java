package com.moava.group.payout.dto;

/**
 * POST /api/v1/groups/{groupId}/members/{memberId}/payout-received 응답.
 */
public record PayoutReceiptActionResponse(
        long groupId,
        long memberId,
        String memberName,
        Integer confirmedRoundNo,
        boolean payoutReceived,
        String payoutReceivedAt
) {
}
