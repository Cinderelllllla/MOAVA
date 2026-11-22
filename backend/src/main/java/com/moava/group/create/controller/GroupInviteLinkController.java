package com.moava.group.create.controller;

import com.moava.group.create.dto.GroupInviteLinkResponse;
import com.moava.group.create.service.GroupInviteService;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/groups/{groupId}/invite")
public class GroupInviteLinkController {

    private final GroupInviteService groupInviteService;

    public GroupInviteLinkController(
            GroupInviteService groupInviteService
    ) {
        this.groupInviteService = groupInviteService;
    }

    // [F18 추가] 모임장만 초대 토큰 조회 가능
    @GetMapping
    public ResponseEntity<?> getInviteLink(
            @PathVariable Long groupId,
            Authentication authentication
    ) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(401).build();
        }

        Long memberId;

        try {
            memberId = Long.valueOf(
                    authentication.getName()
            );
        } catch (NumberFormatException e) {
            return ResponseEntity.status(401).build();
        }

        String inviteToken =
                groupInviteService.getInviteCodeForOwner(
                        groupId,
                        memberId
                );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(
                    new GroupInviteLinkResponse(
                            groupId,
                            inviteToken
                    )
                );
    }
}