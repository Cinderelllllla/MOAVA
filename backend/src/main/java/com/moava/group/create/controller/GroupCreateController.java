package com.moava.group.create.controller;

import com.moava.group.create.dto.GroupCreateRequest;
import com.moava.group.create.dto.GroupCreateResponse;
import com.moava.group.create.service.GroupCreateService;
import com.moava.group.create.service.GroupJoinRequestService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/groups")
public class GroupCreateController {

    private final GroupCreateService groupCreateService;
    private final GroupJoinRequestService groupJoinRequestService;

    @Autowired
    public GroupCreateController(
            GroupCreateService groupCreateService,
            GroupJoinRequestService groupJoinRequestService
    ) {
        this.groupCreateService = groupCreateService;
        this.groupJoinRequestService = groupJoinRequestService;
    }

    public GroupCreateController(GroupCreateService groupCreateService) {
        this(groupCreateService, null);
    }

    @PostMapping
    public ResponseEntity<GroupCreateResponse> createGroup(
            @RequestBody GroupCreateRequest request,
            Authentication authentication 
            //Spring Security가 관리하는 현재 요청의 인증 정보를 받아오는 부분
    ) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Long memberId;

        try {
            memberId = Long.valueOf(authentication.getName());
            //인증된 사용자 이름에 회원 ID가 숫자 문자열로 저장
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        GroupCreateResponse response =
                groupCreateService.createGroup(request, memberId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{groupId}/close-recruitment")
    public ResponseEntity<?> closeRecruitment(
            @PathVariable Long groupId,
            Authentication authentication
    ) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요합니다."));
        }

        Long ownerId;
        try {
            ownerId = Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요합니다."));
        }

        try {
            Map<String, Object> result =
                    groupJoinRequestService.closeRecruitment(groupId, ownerId);
            return ResponseEntity
                    .ok()
                    .cacheControl(CacheControl.noStore())
                    .body(result);
        } catch (SecurityException e) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of("message", e.getMessage()));
        }
    }
}
