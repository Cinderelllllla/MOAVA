package com.moava.chat.service;

import com.moava.chat.dto.*;
import com.moava.chat.mapper.ChatMapper;
import com.moava.common.util.NameMasker;
import com.moava.crypto.service.CryptoKeyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * F34 모임별 채팅 규칙.
 * <ul>
 *   <li>읽기(구독·이전 메시지): 그 모임의 승인된(GYE_MEMBER ACTIVE) 회원. 해산 완료 모임도 읽기는 가능.</li>
 *   <li>보내기: 위 조건 + 해산 완료 아님 + 채팅 제한 아님(미납 제재 CHAT_RESTRICTED_YN 또는 관리자 CHAT_RESTRICT).</li>
 *   <li>본문: 빈 메시지 거부, 500자 이하, 암호화 후 BODY_ENC(2800) 에 들어가는 길이.</li>
 *   <li>저장 후 방송: DB 에 저장된 메시지만 방송한다 (메시지 번호 포함).</li>
 *   <li>시스템 메시지(postSystem): 운영 규칙 변경 등 서버가 남기는 안내. 보낸 사람 없음(MEMBER_ID NULL).</li>
 *   <li>관리자 열람(adminHistory, MOA-96): 처리 중인 신고의 대상 모임만 읽기 전용. 열람마다 AUDIT_LOG CHAT_VIEW.</li>
 * </ul>
 * 채팅 본문은 로그·AUDIT_LOG 에 남기지 않는다 (C 등급 개인정보).
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    public static final int MAX_LENGTH = 500;
    /**
     * BODY_ENC VARCHAR2(2800) = Base64(IV 12B + 본문 + 태그 16B) → 본문은 UTF-8 로 2072바이트까지.
     * 500자는 한 글자가 최대 4바이트(이모지)여도 2000바이트라 항상 들어간다. 컬럼이 다시 줄어들 때를 대비한 안전장치로 둔다.
     */
    static final int BODY_ENC_COLUMN = 2800;
    static final int MAX_BODY_BYTES = BODY_ENC_COLUMN / 4 * 3 - 12 - 16;
    static final int DEFAULT_PAGE = 30;
    static final int MAX_PAGE = 100;
    /** 관리자가 대상 모임 채팅을 열람할 수 있는 신고 상태 (처리 중) */
    public static final Set<String> REPORT_VIEWABLE_STATUSES = Set.of("RECEIVED", "REVIEWING", "MORE_INFO");

    private final ChatMapper mapper;
    private final CryptoKeyService crypto;

    public ChatService(ChatMapper mapper, CryptoKeyService crypto) {
        this.mapper = mapper;
        this.crypto = crypto;
    }

    /** 내 채팅방 목록 */
    public List<ChatRoom> rooms(long memberId) {
        return mapper.selectRooms(memberId).stream()
                .map(r -> new ChatRoom(r.groupId(), r.title(), r.status(), r.memberCount(), r.lastSentAt(),
                        canSend(new ChatAccess(r.status(), "ACTIVE", r.chatRestrictedYn(), r.lastChatSanction()))))
                .toList();
    }

    /** 구독(SUBSCRIBE) 전에 호출: 읽을 수 없으면 ChatException */
    public ChatAccess requireReadable(long memberId, long groupId) {
        ChatAccess access = mapper.selectAccess(groupId, memberId);
        if (access == null) {
            throw new ChatException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "모임을 찾을 수 없어요.");
        }
        if (!access.canRead()) {
            throw new ChatException(HttpStatus.FORBIDDEN, "NOT_GROUP_MEMBER", "이 모임 회원만 채팅방에 들어갈 수 있어요.");
        }
        return access;
    }

    /** 채팅방 참여자 목록. 그 모임 회원만 볼 수 있다. */
    public List<ChatParticipant> participants(long memberId, long groupId) {
        requireReadable(memberId, groupId);
        return mapper.selectParticipants(groupId);
    }

    /** 이전 메시지 (오래된 것 → 최신 순) */
    public ChatHistoryResponse history(long memberId, long groupId, Long beforeId, Integer size) {
        ChatAccess access = requireReadable(memberId, groupId);
        int limit = pageSize(size);
        Page page = readPage(groupId, beforeId, limit);
        return new ChatHistoryResponse(page.items(), page.hasMore(), canSend(access));
    }

    /**
     * 관리자 신고 처리용 대상 모임 채팅 열람 (MOA-96, 읽기 전용, 오래된 것 → 최신 순).
     * 검사 순서: 활성 관리자 → 신고 있음 → 대상 모임 있음 → 처리 중 상태 → size.
     * 열람 기록(AUDIT_LOG CHAT_VIEW)이 실패하면 같은 트랜잭션에서 예외가 올라가 채팅도 보여 주지 않는다.
     * 관리자는 모임 회원이 아니므로 회원 검사(requireReadable)는 하지 않는다. 보낸 사람 이름은 가린다.
     */
    @Transactional
    public ChatHistoryResponse adminHistory(long adminId, long reportId, Long beforeId, Integer size) {
        if (mapper.countActiveAdmin(adminId) < 1) {
            throw new ChatException(HttpStatus.FORBIDDEN, "ADMIN_ONLY", "관리자만 이용할 수 있어요.");
        }
        ChatReportRef report = mapper.selectReportForChat(reportId);
        if (report == null) {
            throw new ChatException(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "신고를 찾을 수 없어요.");
        }
        if (report.groupId() == null) {
            throw new ChatException(HttpStatus.BAD_REQUEST, "NO_TARGET_GROUP", "대상 모임이 없는 신고라 채팅을 볼 수 없어요.");
        }
        if (!REPORT_VIEWABLE_STATUSES.contains(report.status())) {
            throw new ChatException(HttpStatus.FORBIDDEN, "REPORT_NOT_OPEN", "처리가 끝난 신고는 채팅을 볼 수 없어요.");
        }
        int limit = pageSize(size);
        long groupId = report.groupId();

        // 열람 기록: 본문·이름은 넣지 않는다. 실패하면 예외가 그대로 올라가 채팅을 보여 주지 않는다
        String description = "REPORT #" + reportId + (beforeId == null ? " · 채팅 열람" : " · 채팅 이전 메시지 열람");
        mapper.insertChatViewAudit(groupId, adminId, description);
        log.info("관리자 채팅 열람 reportId={} adminId={} groupId={}", reportId, adminId, groupId);

        Page page = readPage(groupId, beforeId, limit);
        List<ChatMessageResponse> masked = page.items().stream().map(ChatService::maskSender).toList();
        return new ChatHistoryResponse(masked, page.hasMore(), false);
    }

    private record Page(List<ChatMessageResponse> items, boolean hasMore) {
    }

    /** size 검사: 기본 30, 1~100 */
    private static int pageSize(Integer size) {
        int limit = size == null ? DEFAULT_PAGE : size;
        if (limit < 1 || limit > MAX_PAGE) {
            throw new ChatException(HttpStatus.BAD_REQUEST, "INVALID_SIZE", "한 번에 1~" + MAX_PAGE + "개까지 불러올 수 있어요.");
        }
        return limit;
    }

    /** beforeId 이전 메시지 limit 개를 복호화해 오래된 것 → 최신 순으로 */
    private Page readPage(long groupId, Long beforeId, int limit) {
        // 하나 더 읽어서 "더 오래된 메시지가 있는지" 판단
        List<ChatMessageRow> rows = mapper.selectMessages(groupId, beforeId, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<ChatMessageResponse> items = new ArrayList<>();
        for (ChatMessageRow row : rows.subList(0, Math.min(limit, rows.size()))) {
            items.add(toResponse(row));
        }
        Collections.reverse(items);
        return new Page(items, hasMore);
    }

    /** 관리자 화면용: 보낸 사람 이름 마스킹. 시스템 메시지는 이름 없음(null) 그대로 */
    private static ChatMessageResponse maskSender(ChatMessageResponse m) {
        if (m.system()) {
            return m;
        }
        return new ChatMessageResponse(m.messageId(), m.groupId(), m.memberId(), NameMasker.mask(m.senderName()),
                m.body(), m.sentAt(), false);
    }

    /** 메시지 보내기: 검사 → 암호화 → 저장 → (호출한 쪽이) 방송 */
    @Transactional
    public ChatMessageResponse send(long memberId, long groupId, String body) {
        validateBody(body);
        ChatAccess access = requireReadable(memberId, groupId);
        if (access.dissolved()) {
            throw new ChatException(HttpStatus.FORBIDDEN, "GROUP_DISSOLVED", "해산된 모임이라 메시지를 보낼 수 없어요.");
        }
        if (access.restricted()) {
            throw new ChatException(HttpStatus.FORBIDDEN, "CHAT_RESTRICTED", "채팅이 제한되어 있어 메시지를 보낼 수 없어요. 읽기는 가능해요.");
        }
        int keyVer = crypto.getCurrentKeyVersion();
        String bodyEnc = crypto.encrypt(body, keyVer);
        long messageId = mapper.nextMessageId();
        LocalDateTime sentAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);   // DB DATE 는 초 단위
        mapper.insertMessage(messageId, groupId, memberId, bodyEnc, keyVer, sentAt);
        return new ChatMessageResponse(messageId, groupId, memberId, mapper.selectMemberName(memberId), body, sentAt);
    }

    /**
     * 시스템 메시지 저장 (F31 운영 규칙 변경 안내 등, 서버 코드만 호출).
     * <ul>
     *   <li>회원·채팅 제한·해산 검사는 하지 않는다 (보낸 사람이 없으므로).</li>
     *   <li>본문 검사(빈 값·500자·저장 칸)와 암호화는 일반 메시지와 똑같다.</li>
     *   <li>MSG_TYPE='SYSTEM', MEMBER_ID=NULL 로 저장한다.</li>
     * </ul>
     * 방송은 하지 않는다. 호출한 쪽이 커밋 뒤에 {@code ChatBroadcaster.broadcastAfterCommit} 으로 보낸다.
     */
    @Transactional
    public ChatMessageResponse postSystem(long groupId, String body) {
        validateBody(body);
        int keyVer = crypto.getCurrentKeyVersion();
        String bodyEnc = crypto.encrypt(body, keyVer);
        long messageId = mapper.nextMessageId();
        LocalDateTime sentAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        mapper.insertSystemMessage(messageId, groupId, bodyEnc, keyVer, sentAt);
        return ChatMessageResponse.systemMessage(messageId, groupId, body, sentAt);
    }

    static void validateBody(String body) {
        if (body == null || body.isBlank()) {
            throw new ChatException(HttpStatus.BAD_REQUEST, "EMPTY_MESSAGE", "메시지를 입력해 주세요.");
        }
        if (body.codePointCount(0, body.length()) > MAX_LENGTH) {
            throw new ChatException(HttpStatus.BAD_REQUEST, "MESSAGE_TOO_LONG", "메시지는 " + MAX_LENGTH + "자까지 보낼 수 있어요.");
        }
        if (body.getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES) {
            // 한글·이모지는 한 글자가 3~4바이트라 500자 안이어도 저장 칸을 넘을 수 있다
            throw new ChatException(HttpStatus.BAD_REQUEST, "MESSAGE_TOO_LONG", "메시지가 너무 길어요. 조금 나눠서 보내 주세요.");
        }
    }

    private static boolean canSend(ChatAccess access) {
        return !access.dissolved() && !access.restricted();
    }

    private ChatMessageResponse toResponse(ChatMessageRow row) {
        String body;
        try {
            body = crypto.decrypt(row.bodyEnc(), row.keyVer());
        } catch (RuntimeException e) {
            // 본문은 남기지 않고 메시지 번호만 기록
            log.warn("채팅 메시지 복호화 실패 messageId={} keyVer={}", row.messageId(), row.keyVer());
            body = "(메시지를 불러오지 못했어요)";
        }
        return new ChatMessageResponse(row.messageId(), row.groupId(), row.memberId(), row.senderName(), body,
                row.sentAt(), row.system());
    }
}
