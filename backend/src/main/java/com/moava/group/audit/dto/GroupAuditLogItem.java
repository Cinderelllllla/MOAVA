package com.moava.group.audit.dto;

/**
 * F33 회원용 모임 운영 기록 단건 응답 항목.
 * - 회원 이름은 NameMasker 로 마스킹해 반환한다.
 * - 관리자 행위는 개인 실명 없이 "관리자" 로 표기한다.
 */
public record GroupAuditLogItem(
        long auditLogId,
        long groupId,
        String eventType,
        String actorType,
        String actorLabel,
        String targetMemberLabel,
        String description,
        String createdAt
) {
}
