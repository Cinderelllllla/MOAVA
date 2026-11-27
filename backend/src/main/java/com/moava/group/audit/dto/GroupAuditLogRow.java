package com.moava.group.audit.dto;

/**
 * F33 회원용 모임 운영 기록 DB 조회 행 (AUDIT_LOG + MEMBER + ADMIN).
 */
public record GroupAuditLogRow(
        long auditLogId,
        long groupId,
        String eventType,
        Long actorMemberId,
        String actorMemberName,
        Long actorAdminId,
        Long targetMemberId,
        String targetMemberName,
        String description,
        String createdAt
) {
}
