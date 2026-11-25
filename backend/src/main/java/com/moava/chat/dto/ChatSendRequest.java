package com.moava.chat.dto;

/** STOMP SEND /app/groups/{groupId}/chat 본문: {"body":"안녕하세요"} */
public record ChatSendRequest(String body) {
}
