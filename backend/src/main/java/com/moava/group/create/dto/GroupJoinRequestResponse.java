
package com.moava.group.create.dto;

public record GroupJoinRequestResponse(
        Long joinRequestId,
        Long groupId,
        String status,
        String message
) {
}
