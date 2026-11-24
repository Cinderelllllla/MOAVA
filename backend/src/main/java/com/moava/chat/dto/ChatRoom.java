package com.moava.chat.dto;

import java.time.LocalDateTime;

/**
 * 내 채팅방(= 내가 승인된 회원인 모임) 목록 한 줄.
 *
 * @param memberCount 현재 ACTIVE 회원 수
 * @param lastSentAt  마지막 메시지 시각 (없으면 null)
 * @param canSend     지금 보내기 가능 여부 (해산 완료·채팅 제한이면 false)
 */
public record ChatRoom(long groupId, String title, String status, int memberCount,
                       LocalDateTime lastSentAt, boolean canSend) {
}
