package com.moava.group.create.controller;

import com.moava.group.create.dto.GroupCreateRequest;
import com.moava.group.create.dto.GroupCreateResponse;
import com.moava.group.create.service.GroupCreateService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/groups")
public class GroupCreateController {

    private final GroupCreateService groupCreateService;

    public GroupCreateController(GroupCreateService groupCreateService) {
        this.groupCreateService = groupCreateService;
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
}
