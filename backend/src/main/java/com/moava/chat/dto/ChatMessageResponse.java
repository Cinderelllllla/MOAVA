package com.moava.chat.dto;

import java.time.LocalDateTime;

/**
 * 화면에 보내는 채팅 메시지 (본문은 복호화된 평문). 방송(/topic)과 이전 메시지 조회가 같은 모양을 쓴다.
 *
 * @param memberId   보낸 회원. 시스템 메시지(운영 규칙 변경 안내 등)는 null
 * @param senderName 보낸 회원 이름. 시스템 메시지는 null
 * @param system     true 면 시스템 메시지 (CHAT_MESSAGE.MSG_TYPE = 'SYSTEM'). 화면은 가운데 안내 줄로 보여 준다.
 */
public record ChatMessageResponse(long messageId, long groupId, Long memberId, String senderName,
                                  String body, LocalDateTime sentAt, boolean system) {

    /** 회원이 보낸 일반 메시지 */
    public ChatMessageResponse(long messageId, long groupId, long memberId, String senderName,
                               String body, LocalDateTime sentAt) {
        this(messageId, groupId, Long.valueOf(memberId), senderName, body, sentAt, false);
    }

    /** 시스템 메시지 (보낸 사람 없음) */
    public static ChatMessageResponse systemMessage(long messageId, long groupId, String body, LocalDateTime sentAt) {
        return new ChatMessageResponse(messageId, groupId, null, null, body, sentAt, true);
    }
}
