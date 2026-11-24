package com.moava.chat.dto;

import java.time.LocalDateTime;

/** CHAT_MESSAGE 한 줄 + 보낸 사람 이름 (본문은 아직 암호문). */
public record ChatMessageRow(long messageId, long groupId, long memberId, String senderName,
                             String bodyEnc, int keyVer, LocalDateTime sentAt) {
}
