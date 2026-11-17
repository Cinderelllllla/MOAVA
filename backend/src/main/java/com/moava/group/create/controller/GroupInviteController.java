package com.moava.group.create.controller;

import com.moava.group.create.dto.GroupInviteResponse;
import com.moava.group.create.service.GroupInviteService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/invites")
public class GroupInviteController {

    private final GroupInviteService groupInviteService;

    public GroupInviteController(GroupInviteService groupInviteService) {
        this.groupInviteService = groupInviteService;
    }

    @GetMapping("/{token}")
    public ResponseEntity<GroupInviteResponse> getInvite(
            @PathVariable String token
    ) {

        GroupInviteResponse response =
                groupInviteService.getInvite(token);

        if (response == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response);
    }
}