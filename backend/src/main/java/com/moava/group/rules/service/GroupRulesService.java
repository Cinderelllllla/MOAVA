package com.moava.group.rules.service;

import com.moava.chat.dto.ChatMessageResponse;
import com.moava.chat.service.ChatBroadcaster;
import com.moava.chat.service.ChatService;
import com.moava.group.rules.dto.GroupRulesResponse;
import com.moava.group.rules.dto.GroupRulesRow;
import com.moava.group.rules.dto.GroupRulesUpdateRequest;
import com.moava.group.rules.mapper.GroupRulesMapper;
import com.moava.notification.common.domain.NotifType;
import com.moava.notification.common.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * F31 운영 규칙 등록·수정.
 * <ul>
 *   <li>조회: 이 모임의 ACTIVE 회원(모임장 포함)</li>
 *   <li>수정: 모임장만. 해산 완료(DISSOLVED) 모임은 수정 불가</li>
 *   <li>수정하면 한 트랜잭션으로 규칙 저장 + 감사 로그(RULE_UPDATE) + 알림(RULE_UPDATED, 모임장 제외) + 채팅 시스템 메시지 저장</li>
 *   <li>채팅방 실시간 전송은 커밋이 끝난 뒤에만 (롤백되면 보내지 않음)</li>
 *   <li>제목·본문이 기존과 같으면 아무것도 저장·발송하지 않는다</li>
 * </ul>
 * 규칙 원문은 그대로 저장한다(서버에서 이스케이프하지 않음). 화면은 React 기본 이스케이프로만 표시한다.
 * 감사 로그에는 제목만 남기고 본문 전체는 남기지 않는다.
 */
@Service
public class GroupRulesService {

    /** GYE_GROUP.RULES_TITLE VARCHAR2(100) — Oracle 기본 단위가 BYTE 라 UTF-8 바이트로 센다 (한글 1자 = 3바이트) */
    public static final int TITLE_MAX_BYTES = 100;
    static final String AUDIT_EVENT = "RULE_UPDATE";

    private final GroupRulesMapper mapper;
    private final NotificationService notifications;
    private final ChatService chat;
    private final ChatBroadcaster broadcaster;

    public GroupRulesService(GroupRulesMapper mapper, NotificationService notifications,
                             ChatService chat, ChatBroadcaster broadcaster) {
        this.mapper = mapper;
        this.notifications = notifications;
        this.chat = chat;
        this.broadcaster = broadcaster;
    }

    /** GET: 최신 규칙 */
    public GroupRulesResponse get(long memberId, long groupId) {
        GroupRulesRow row = requireGroup(memberId, groupId);
        if (!row.canRead(memberId)) {
            throw new GroupRulesException(HttpStatus.FORBIDDEN, "NOT_GROUP_MEMBER", "이 모임 회원만 운영 규칙을 볼 수 있어요.");
        }
        return toResponse(row, memberId);
    }

    /** PUT: 모임장이 규칙 수정 */
    @Transactional
    public GroupRulesResponse update(long memberId, long groupId, GroupRulesUpdateRequest request) {
        GroupRulesRow row = requireGroup(memberId, groupId);
        if (!row.isOwner(memberId)) {
            throw new GroupRulesException(HttpStatus.FORBIDDEN, "LEADER_ONLY", "모임장만 규칙을 수정할 수 있어요");
        }
        if (row.dissolved()) {
            throw new GroupRulesException(HttpStatus.CONFLICT, "GROUP_DISSOLVED", "해산된 모임은 규칙을 수정할 수 없어요.");
        }
        String title = request == null ? null : request.rulesTitle();
        String body = request == null ? null : request.rulesBody();
        validate(title, body);
        title = title.strip();
        body = body.strip();   // 모임 만들 때(GroupCreateService)와 같이 앞뒤 공백 제거 후 저장

        // 바뀐 게 없으면 저장·알림·로그·채팅 없이 그대로 돌려준다
        if (title.equals(strip(row.rulesTitle())) && body.equals(strip(row.rulesBody()))) {
            return toResponse(row, memberId);
        }

        mapper.updateRules(groupId, title, body);
        mapper.insertAuditLog(groupId, memberId, AUDIT_EVENT, "운영 규칙 수정: " + title);
        notifications.notifyGroup(groupId, NotifType.RULE_UPDATED, groupId,
                Map.of("group", row.groupTitle(), "title", title, "groupId", groupId),
                List.of(memberId));   // 모임장 본인 제외
        ChatMessageResponse notice = chat.postSystem(groupId, systemMessage(title));
        broadcaster.broadcastAfterCommit(notice);   // 커밋 뒤에만 실제 전송

        return new GroupRulesResponse(groupId, title, body, true, row.groupStatus());
    }

    /** 채팅방 안내 문구. 제목이 100바이트 이하라 채팅 본문 한도(500자)를 넘지 않는다. */
    static String systemMessage(String title) {
        return "📌 운영 규칙이 '" + title + "'(으)로 업데이트됐어요.";
    }

    /** 빈 제목·본문, 제목 100바이트 초과 → 400 */
    static void validate(String title, String body) {
        if (blank(title) || blank(body)) {
            throw new GroupRulesException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "규칙 제목과 내용을 모두 입력해 주세요.");
        }
        if (exceedsBytes(title, TITLE_MAX_BYTES)) {
            throw new GroupRulesException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                    "규칙 제목이 너무 길어요. 한글 기준 33자까지 쓸 수 있어요.");
        }
    }

    private GroupRulesRow requireGroup(long memberId, long groupId) {
        GroupRulesRow row = mapper.selectRules(groupId, memberId);
        if (row == null) {
            throw new GroupRulesException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "모임을 찾을 수 없어요.");
        }
        return row;
    }

    private static GroupRulesResponse toResponse(GroupRulesRow row, long memberId) {
        return new GroupRulesResponse(row.groupId(), row.rulesTitle(), row.rulesBody(), row.isOwner(memberId),
                row.groupStatus());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /** GroupCreateValidator.exceedsBytes 와 같은 방식: 앞뒤 공백 제거 후 UTF-8 바이트 수 */
    static boolean exceedsBytes(String value, int maxBytes) {
        return value != null && value.strip().getBytes(StandardCharsets.UTF_8).length > maxBytes;
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }
}
