package com.moava.group.warning.service;

import com.moava.group.warning.dto.GroupWarningRequest;
import com.moava.group.warning.dto.GroupWarningResponse;
import com.moava.group.warning.dto.GroupWarningTarget;
import com.moava.group.warning.mapper.GroupWarningMapper;
import com.moava.notification.common.domain.NotifType;
import com.moava.notification.common.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * F30 모임 경고 및 강제 탈퇴 (모임별 최대 2회).
 * <ul>
 *   <li>모임장만, 대상은 모임장이 아닌 이 모임의 ACTIVE 회원. 해산 완료 모임은 불가</li>
 *   <li>경고: WARNING_COUNT &lt; 2 일 때만 +1. 2회가 돼도 자동 탈퇴 없음</li>
 *   <li>강제 탈퇴: WARNING_COUNT = 2 이고 진행 중 중도 탈퇴 신청이 없을 때만 EXPELLED (GYE_GROUP 행 잠금으로 F29 신청과 순서 보장)</li>
 *   <li>한 트랜잭션: GYE_MEMBER 변경 + SANCTION + AUDIT_LOG + 대상 알림(SANCTION)</li>
 * </ul>
 * F26 관리자 미납 경고(OVERDUE_WARN)와 별개 — 그쪽은 WARNING_COUNT 를 바꾸지 않는다.
 */
@Service
public class GroupWarningService {

    public static final int MAX_WARNINGS = 2;
    /** SANCTION.REASON VARCHAR2(500) — Oracle 기본 BYTE 단위라 UTF-8 바이트로 센다 */
    public static final int REASON_MAX_BYTES = 500;

    private final GroupWarningMapper mapper;
    private final NotificationService notifications;

    public GroupWarningService(GroupWarningMapper mapper, NotificationService notifications) {
        this.mapper = mapper;
        this.notifications = notifications;
    }

    /** POST /groups/{groupId}/members/{memberId}/warnings */
    @Transactional
    public GroupWarningResponse warn(long leaderId, long groupId, long targetId, GroupWarningRequest request) {
        GroupWarningTarget target = requireTarget(leaderId, groupId, targetId);
        String reason = validReason(request);

        if (target.warningCount() >= MAX_WARNINGS) {
            throw limitReached();
        }
        // 검사와 증가를 한 문장으로 → 동시에 눌러도 3회가 되지 않음. 0행이면 그사이 상한 도달·상태 변경
        if (mapper.increaseWarning(groupId, targetId) == 0) {
            throw limitReached();
        }
        int count = mapper.selectTarget(groupId, targetId).warningCount();   // 실제 저장된 값

        mapper.insertSanction("WARN", targetId, groupId, leaderId, reason);
        mapper.insertAuditLog(groupId, leaderId, targetId, "WARN",
                "모임 경고 (누적 " + count + "/" + MAX_WARNINGS + "회): " + reason);
        notifications.notifyMember(targetId, NotifType.SANCTION, groupId, Map.of(
                "scope", target.groupTitle(),
                "action", "모임장 경고가 등록됐어요. (누적 " + count + "/" + MAX_WARNINGS + "회)"));

        return new GroupWarningResponse(groupId, targetId, count, "ACTIVE", count >= MAX_WARNINGS);
    }

    /** POST /groups/{groupId}/members/{memberId}/expulsion */
    @Transactional
    public GroupWarningResponse expel(long leaderId, long groupId, long targetId, GroupWarningRequest request) {
        mapper.lockGroup(groupId);   // F29 중도 탈퇴 신청과 같은 행을 잠가 동시 처리 방지 (모임이 없으면 아래에서 404)
        GroupWarningTarget target = requireTarget(leaderId, groupId, targetId);
        String reason = validReason(request);

        if (target.warningCount() < MAX_WARNINGS) {
            throw notEnoughWarnings();
        }
        // 명세서 외 추가 방어: 진행 중 탈퇴 신청이 있으면 강제 탈퇴 시 그 신청이 처리 불가 상태로 남음 (팀 합의 11/26)
        if (mapper.countActiveWithdrawal(groupId, targetId) > 0) {
            throw new GroupWarningException(HttpStatus.CONFLICT, "WITHDRAWAL_IN_PROGRESS", "중도 탈퇴가 진행 중인 회원이에요.");
        }
        if (mapper.expel(groupId, targetId) == 0) {
            throw notEnoughWarnings();
        }
        // ↓ 아래(insertSanction부터)는 그대로

        mapper.insertSanction("EXPEL", targetId, groupId, leaderId, reason);
        mapper.insertAuditLog(groupId, leaderId, targetId, "EXPEL", "강제 탈퇴: " + reason);
        notifications.notifyMember(targetId, NotifType.SANCTION, groupId, Map.of(
                "scope", target.groupTitle(),
                "action", "모임에서 강제 탈퇴 처리됐어요."));

        return new GroupWarningResponse(groupId, targetId, MAX_WARNINGS, "EXPELLED", false);
    }


    /** 공통 검증: 모임 존재 → 모임장 → 해산 여부 → 대상이 모임장 아님 → 대상이 ACTIVE 회원 */
    private GroupWarningTarget requireTarget(long leaderId, long groupId, long targetId) {
        GroupWarningTarget target = mapper.selectTarget(groupId, targetId);
        if (target == null) {
            throw new GroupWarningException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "모임을 찾을 수 없어요.");
        }
        if (!target.isOwner(leaderId)) {
            throw new GroupWarningException(HttpStatus.FORBIDDEN, "LEADER_ONLY", "모임장만 경고·강제 탈퇴를 할 수 있어요.");
        }
        if (target.dissolved()) {
            throw new GroupWarningException(HttpStatus.CONFLICT, "GROUP_DISSOLVED", "해산된 모임에서는 할 수 없어요.");
        }
        if (target.isOwner(targetId)) {
            throw new GroupWarningException(HttpStatus.BAD_REQUEST, "CANNOT_TARGET_LEADER", "모임장 본인은 대상이 될 수 없어요.");
        }
        if (!target.isActiveMember()) {
            throw new GroupWarningException(HttpStatus.NOT_FOUND, "NOT_GROUP_MEMBER", "이 모임에 참여 중인 회원이 아니에요.");
        }
        return target;
    }

    /** 사유 필수, 500바이트 이하. 앞뒤 공백 제거 후 저장 (GroupRulesService 와 같은 방식) */
    static String validReason(GroupWarningRequest request) {
        String reason = request == null ? null : request.reason();
        if (reason == null || reason.isBlank()) {
            throw new GroupWarningException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "사유를 입력해 주세요.");
        }
        reason = reason.strip();
        if (reason.getBytes(StandardCharsets.UTF_8).length > REASON_MAX_BYTES) {
            throw new GroupWarningException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                    "사유가 너무 길어요. 한글 기준 166자까지 쓸 수 있어요.");
        }
        return reason;
    }

    private static GroupWarningException limitReached() {
        return new GroupWarningException(HttpStatus.CONFLICT, "WARNING_LIMIT", "경고는 모임별 최대 2회입니다.");
    }

    private static GroupWarningException notEnoughWarnings() {
        return new GroupWarningException(HttpStatus.CONFLICT, "NOT_ENOUGH_WARNINGS",
                "경고 2회를 받은 회원만 강제 탈퇴할 수 있어요.");
    }
}