package com.moava.group.audit.dto;

import java.util.List;

/**
 * GET /api/v1/groups/{groupId}/audit-logs 페이징 응답.
 * append-only 감사 로그이므로 조회 전용이며 삭제·수정 API 를 제공하지 않는다.
 */
public record GroupAuditLogPageResponse(
        long groupId,
        String groupTitle,
        List<GroupAuditLogItem> items,
        List<String> eventTypes,
        String selectedEventType,
        int page,
        int size,
        long totalCount,
        int totalPages,
        boolean hasPrev,
        boolean hasNext
) {
}
