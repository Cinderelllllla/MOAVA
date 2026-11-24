package com.moava.chat.dto;

import java.time.LocalDateTime;

/** ChatMapper.selectRooms 결과 (권한 판단 재료 포함). 서비스가 ChatRoom 으로 바꾼다. */
public record ChatRoomRow(long groupId, String title, String status, int memberCount, LocalDateTime lastSentAt,
                          String chatRestrictedYn, String lastChatSanction) {
}
