package com.moava.group.audit.dto;

/**
 * F33 회원용 모임 운영 기록 접근 권한 검증 행 (GYE_GROUP + GYE_MEMBER).
 * - 모임이 없으면 null
 * - 회원이 이 모임에 참여 중이지 않으면 memberStatus 가 null
 */
public record GroupAuditAccessRow(
        long groupId,
        String groupTitle,
        long ownerId,
        String memberStatus
) {
    public boolean canRead(long memberId) {
        return ownerId == memberId || "ACTIVE".equals(memberStatus);
    }
}
