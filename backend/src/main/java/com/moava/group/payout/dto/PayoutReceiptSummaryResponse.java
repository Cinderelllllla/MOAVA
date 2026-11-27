package com.moava.group.payout.dto;

import java.util.List;

/**
 * GET /api/v1/groups/{groupId}/payout-receipts 응답.
 * - orderConfirmed=false 이면 프론트에서 수령 완료 표시 버튼을 노출하지 않는다.
 * - myPayoutReceived=true 이면 F29 중도 탈퇴 화면에서 일반 탈퇴 버튼을 비활성화한다.
 */
public record PayoutReceiptSummaryResponse(
        long groupId,
        String groupTitle,
        String groupStatus,
        boolean orderConfirmed,
        boolean leader,
        boolean myPayoutReceived,
        String myPayoutReceivedAt,
        List<PayoutReceiptMemberItem> members
) {
}
