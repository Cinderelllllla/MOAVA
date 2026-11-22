package com.moava.group.create.dto;

// [F18 추가] 모임장 전용 초대 토큰 응답
public record GroupInviteLinkResponse(
        Long groupId,
        String inviteToken
) {
}