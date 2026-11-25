package com.moava.chat.dto;

import java.time.LocalDateTime;

/** 화면에 보내는 채팅 메시지 (본문은 복호화된 평문). 방송(/topic)과 이전 메시지 조회가 같은 모양을 쓴다. */
public record ChatMessageResponse(long messageId, long groupId, long memberId, String senderName,
                                  String body, LocalDateTime sentAt) {
}
