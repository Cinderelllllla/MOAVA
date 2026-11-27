package com.moava.group.audit.service;

import com.moava.common.util.NameMasker;
import com.moava.group.audit.dto.GroupAuditAccessRow;
import com.moava.group.audit.dto.GroupAuditLogItem;
import com.moava.group.audit.dto.GroupAuditLogPageResponse;
import com.moava.group.audit.dto.GroupAuditLogRow;
import com.moava.group.audit.mapper.GroupAuditMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * F33 회원용 모임 운영 기록(감사 로그) 조회 서비스.
 * <ul>
 *   <li>조회 권한: 해당 모임의 모임장 또는 ACTIVE 참여 회원 (해산된 모임의 기존 기록 열람 포함).</li>
 *   <li>정책: append-only 감사 로그이므로 삭제·수정 기능을 제공하지 않는다.</li>
 *   <li>개인정보·기밀 보호:
 *     <ul>
 *       <li>회원 행위자·대상자 이름은 NameMasker 로 마스킹한다.</li>
 *       <li>관리자 행위는 개인 식별 정보 없이 "관리자" 로 표기한다.</li>
 *       <li>REPORT_STATUS · CHAT_VIEW 는 관리자/신고자 전용 비공개 기록이므로 회원 조회에서 제외한다.</li>
 *       <li>REPORT_RECEIVED 는 신고자 보호를 위해 행위자를 익명("회원") 처리하고 요약 문구("신고 접수")만 표시한다.</li>
 *     </ul>
 *   </li>
 * </ul>
 */
@Service
public class GroupAuditService {

    public static final int DEFAULT_PAGE_SIZE = 8;
    public static final int MAX_PAGE_SIZE = 50;

    private static final Set<String> HIDDEN_MEMBER_EVENT_TYPES = Set.of("REPORT_STATUS", "CHAT_VIEW");
    private static final String EVENT_REPORT_RECEIVED = "REPORT_RECEIVED";

    private final GroupAuditMapper mapper;

    public GroupAuditService(GroupAuditMapper mapper) {
        this.mapper = mapper;
    }

    /** GET /api/v1/groups/{groupId}/audit-logs */
    @Transactional(readOnly = true)
    public GroupAuditLogPageResponse getGroupAuditLogs(
            long requesterId,
            long groupId,
            String eventType,
            int page,
            int size
    ) {
        if (groupId <= 0) {
            throw new GroupAuditException(HttpStatus.BAD_REQUEST, "INVALID_GROUP_ID", "올바른 모임 번호를 입력해 주세요.");
        }
        if (page < 0) {
            throw new GroupAuditException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "페이지 번호는 0 이상이어야 해요.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new GroupAuditException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SIZE",
                    "페이지당 조회 건수는 1건 이상 " + MAX_PAGE_SIZE + "건 이하만 가능해요."
            );
        }

        GroupAuditAccessRow access = mapper.selectAccess(groupId, requesterId);
        if (access == null) {
            throw new GroupAuditException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "모임을 찾을 수 없어요.");
        }
        if (!access.canRead(requesterId)) {
            throw new GroupAuditException(HttpStatus.FORBIDDEN, "NOT_GROUP_MEMBER", "이 모임에 참여 중인 회원만 운영 기록을 조회할 수 있어요.");
        }

        String normalizedEventType = normalizeEventType(eventType);
        List<String> availableTypes = mapper.selectDistinctGroupEventTypes(groupId);
        List<String> eventTypes = availableTypes == null ? List.of() : availableTypes;

        // 비공개 이벤트 유형으로 직접 필터링해도 빈 결과를 반환 (내부 존재 여부 미노출)
        if (normalizedEventType != null && HIDDEN_MEMBER_EVENT_TYPES.contains(normalizedEventType)) {
            return new GroupAuditLogPageResponse(
                    access.groupId(),
                    access.groupTitle(),
                    List.of(),
                    eventTypes,
                    normalizedEventType,
                    page,
                    size,
                    0L,
                    0,
                    page > 0,
                    false
            );
        }

        long offsetLong = (long) page * size;
        if (offsetLong > Integer.MAX_VALUE) {
            throw new GroupAuditException(HttpStatus.BAD_REQUEST, "PAGE_OUT_OF_RANGE", "조회 가능한 페이지 범위를 초과했어요.");
        }

        long totalCount = mapper.countGroupAuditLogs(groupId, normalizedEventType);
        List<GroupAuditLogItem> items = (totalCount == 0 || offsetLong >= totalCount)
                ? List.of()
                : mapper.selectGroupAuditLogs(groupId, normalizedEventType, (int) offsetLong, size)
                        .stream()
                        .map(GroupAuditService::toItem)
                        .toList();

        int totalPages = totalCount == 0 ? 0 : (int) ((totalCount + size - 1) / size);
        boolean hasPrev = page > 0;
        boolean hasNext = (long) (page + 1) * size < totalCount;

        return new GroupAuditLogPageResponse(
                access.groupId(),
                access.groupTitle(),
                items,
                eventTypes,
                normalizedEventType,
                page,
                size,
                totalCount,
                totalPages,
                hasPrev,
                hasNext
        );
    }

    private static GroupAuditLogItem toItem(GroupAuditLogRow row) {
        // 신고 접수 이벤트는 신고자 익명 보호: 행위자 마스킹명·내부 신고번호 대신 요약 문구만 노출
        if (EVENT_REPORT_RECEIVED.equals(row.eventType())) {
            return new GroupAuditLogItem(
                    row.auditLogId(),
                    row.groupId(),
                    row.eventType(),
                    "MEMBER",
                    "회원",
                    null,
                    "모임 관련 신고가 접수됐어요.",
                    row.createdAt()
            );
        }

        String actorType = resolveActorType(row);
        String actorLabel = formatActorLabel(row);
        String targetLabel = row.targetMemberId() == null
                ? null
                : NameMasker.mask(trimToNull(row.targetMemberName()));

        return new GroupAuditLogItem(
                row.auditLogId(),
                row.groupId(),
                row.eventType(),
                actorType,
                actorLabel,
                targetLabel,
                row.description(),
                row.createdAt()
        );
    }

    private static String resolveActorType(GroupAuditLogRow row) {
        if (row.actorAdminId() != null) {
            return "ADMIN";
        }
        if (row.actorMemberId() != null) {
            return "MEMBER";
        }
        return "SYSTEM";
    }

    private static String formatActorLabel(GroupAuditLogRow row) {
        if (row.actorAdminId() != null) {
            return "관리자";
        }
        if (row.actorMemberId() != null) {
            return NameMasker.mask(trimToNull(row.actorMemberName()));
        }
        return "시스템";
    }

    private static String normalizeEventType(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null || "ALL".equalsIgnoreCase(trimmed) || "전체".equals(trimmed)) {
            return null;
        }
        if (trimmed.length() > 40) {
            throw new GroupAuditException(HttpStatus.BAD_REQUEST, "INVALID_EVENT_TYPE", "이벤트 유형 값이 올바르지 않아요.");
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
