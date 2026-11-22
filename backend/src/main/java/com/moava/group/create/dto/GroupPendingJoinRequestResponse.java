package com.moava.group.create.dto;

import java.time.LocalDateTime;

public record GroupPendingJoinRequestResponse(
        Long joinRequestId,
        Long groupId,
        Long memberId,
        String memberName,
        String via,
        String status,
        LocalDateTime createdAt
) {
}
