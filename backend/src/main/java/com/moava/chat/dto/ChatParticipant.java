package com.moava.chat.dto;

import java.time.LocalDateTime;

/**
 * 채팅방 참여자 = 그 모임의 ACTIVE 회원. 이름과 모임장 여부만 보여 준다 (연락처 등은 넣지 않음).
 *
 * @param leader   모임장이면 true
 * @param joinedAt 가입 승인 시각
 */
public record ChatParticipant(long memberId, String name, boolean leader, LocalDateTime joinedAt) {
}
