package com.moava.group.create.dto;

public record GroupInviteResponse(
        Long groupId,
        String title,
        String purpose,
        String regionSido,
        String regionSigungu,
        String visibility
) {
}