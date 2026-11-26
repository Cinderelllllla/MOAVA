package com.moava.chat.dto;

import java.time.LocalDateTime;

/**
 * CHAT_MESSAGE 한 줄 + 보낸 사람 이름 (본문은 아직 암호문).
 *
 * @param memberId   보낸 회원. 시스템 메시지는 null
 * @param senderName 보낸 회원 이름. 시스템 메시지는 null
 * @param system     MSG_TYPE = 'SYSTEM' 이면 true
 */
public record ChatMessageRow(long messageId, long groupId, Long memberId, String senderName,
                             String bodyEnc, int keyVer, LocalDateTime sentAt, boolean system) {

    /** 회원이 보낸 일반 메시지 (system = false) */
    public ChatMessageRow(long messageId, long groupId, long memberId, String senderName,
                          String bodyEnc, int keyVer, LocalDateTime sentAt) {
        this(messageId, groupId, Long.valueOf(memberId), senderName, bodyEnc, keyVer, sentAt, false);
    }
}
