package com.moava.group.payout.dto;

/**
 * F28 곗돈 수령 완료 표시 검증용 내부 조회 행 (GYE_GROUP + GYE_MEMBER + MEMBER + 확정 PAYOUT_REQUEST).
 * - 모임이 없으면 MyBatis 결과가 null
 * - 대상이 이 모임 회원이 아니면 memberStatus 가 null
 */
public record PayoutReceiptTarget(
        long groupId,
        String groupTitle,
        String groupStatus,
        long ownerId,
        String orderConfirmedYn,
        String memberStatus,
        String memberName,
        String payoutReceivedYn,
        String payoutReceivedAt,
        Integer confirmedRoundNo
) {
    public boolean isOwner(long memberId) {
        return ownerId == memberId;
    }

    public boolean dissolved() {
        return "DISSOLVED".equals(groupStatus);
    }

    public boolean orderConfirmed() {
        return "Y".equalsIgnoreCase(orderConfirmedYn);
    }

    public boolean isActiveMember() {
        return "ACTIVE".equals(memberStatus);
    }

    public boolean payoutReceived() {
        return "Y".equalsIgnoreCase(payoutReceivedYn);
    }
}
