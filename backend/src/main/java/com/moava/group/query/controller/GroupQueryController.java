package com.moava.group.query.controller;

import com.moava.group.query.dto.GroupListResponse;
import com.moava.group.query.service.GroupQueryService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
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
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Long memberId;

        try {
            memberId = Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(
                groupQueryService.getMyGroups(memberId)
        );
    }
}