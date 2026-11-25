
package com.moava.group.query.dto;

import java.time.LocalDateTime;

public record GroupMemberResponse(
        Long gyeMemberId,
        Long groupId,
        Long memberId,
        String memberName,
        String role,
        String status,
        LocalDateTime joinedAt,
        Integer warningCount
) {
}
