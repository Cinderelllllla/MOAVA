
package com.moava.group.query.controller;

import com.moava.group.query.dto.GroupListResponse;
import com.moava.group.query.dto.GroupMemberResponse;
import com.moava.group.query.service.GroupQueryService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class GroupQueryController {

    private final GroupQueryService groupQueryService;

    public GroupQueryController(GroupQueryService groupQueryService) {
        this.groupQueryService = groupQueryService;
    }

    @GetMapping("/my")
    public ResponseEntity<List<GroupListResponse>> getMyGroups(
            Authentication authentication
    ) {
        Long memberId = resolveMemberId(authentication);

        if (memberId == null) {
            return ResponseEntity.status(
                    HttpStatus.UNAUTHORIZED
            ).build();
        }

        return ResponseEntity.ok(
                groupQueryService.getMyGroups(memberId)
        );
    }

    @GetMapping("/{groupId}/members")
    public ResponseEntity<List<GroupMemberResponse>> getGroupMembers(
            @PathVariable Long groupId,
            Authentication authentication
    ) {
        Long memberId = resolveMemberId(authentication);

        if (memberId == null) {
            return ResponseEntity.status(
                    HttpStatus.UNAUTHORIZED
            ).build();
        }

        try {
            return ResponseEntity.ok(
                    groupQueryService.getGroupMembers(
                            groupId,
                            memberId
                    )
            );
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(
                    HttpStatus.FORBIDDEN
            ).build();
        }
    }

    private Long resolveMemberId(Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
