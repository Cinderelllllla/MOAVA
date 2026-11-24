package com.moava.chat.service;

import com.moava.chat.dto.*;
import com.moava.chat.mapper.ChatMapper;
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

/**
 * F34 모임별 채팅 규칙.
 * <ul>
 *   <li>읽기(구독·이전 메시지): 그 모임의 승인된(GYE_MEMBER ACTIVE) 회원. 해산 완료 모임도 읽기는 가능.</li>
 *   <li>보내기: 위 조건 + 해산 완료 아님 + 채팅 제한 아님(미납 제재 CHAT_RESTRICTED_YN 또는 관리자 CHAT_RESTRICT).</li>
 *   <li>본문: 빈 메시지 거부, 500자 이하, 암호화 후 BODY_ENC(2000) 에 들어가는 길이.</li>
 *   <li>저장 후 방송: DB 에 저장된 메시지만 방송한다 (메시지 번호 포함).</li>
 * </ul>
 * 채팅 본문은 로그·AUDIT_LOG 에 남기지 않는다 (C 등급 개인정보).
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    public static final int MAX_LENGTH = 500;
    /** BODY_ENC VARCHAR2(2000) = Base64(IV 12B + 본문 + 태그 16B) → 본문은 UTF-8 로 1472바이트까지 */
    static final int MAX_BODY_BYTES = 2000 / 4 * 3 - 12 - 16;
    static final int DEFAULT_PAGE = 30;
    static final int MAX_PAGE = 100;

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
        int limit = size == null ? DEFAULT_PAGE : size;
        if (limit < 1 || limit > MAX_PAGE) {
            throw new ChatException(HttpStatus.BAD_REQUEST, "INVALID_SIZE", "한 번에 1~" + MAX_PAGE + "개까지 불러올 수 있어요.");
        }
        // 하나 더 읽어서 "더 오래된 메시지가 있는지" 판단
        List<ChatMessageRow> rows = mapper.selectMessages(groupId, beforeId, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<ChatMessageResponse> items = new ArrayList<>();
        for (ChatMessageRow row : rows.subList(0, Math.min(limit, rows.size()))) {
            items.add(toResponse(row));
        }
        Collections.reverse(items);
        return new ChatHistoryResponse(items, hasMore, canSend(access));
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
        return new ChatMessageResponse(row.messageId(), row.groupId(), row.memberId(), row.senderName(), body, row.sentAt());
    }
}
