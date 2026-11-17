
package com.moava.group.create.dto;

public record GroupJoinCodeIssueResponse(
        Long groupId,
        String joinCode,
        int codeVersion,
        String issuedAt,
        String message
) {
}
