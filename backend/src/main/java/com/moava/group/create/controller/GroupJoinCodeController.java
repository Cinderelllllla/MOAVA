
package com.moava.group.create.controller;

import com.moava.group.create.dto.GroupJoinCodeIssueResponse;
import com.moava.group.create.dto.JoinCodeVerifyResponse;
import com.moava.group.create.service.GroupJoinCodeIssueService;
import com.moava.group.create.service.JoinCodeVerifyService;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/groups/{groupId}/join-code")
public class GroupJoinCodeController {

    private final GroupJoinCodeIssueService issueService;
    private final JoinCodeVerifyService verifyService;

    public GroupJoinCodeController(
            GroupJoinCodeIssueService issueService,
            JoinCodeVerifyService verifyService
    ) {
        this.issueService = issueService;
        this.verifyService = verifyService;
    }

    /**
     * JWT 인증 회원 ID 조회
     *
     * 기존 GroupCreateController와 동일한 방식
     */
    private Long getMemberId(Authentication authentication) {

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

    /**
     * F18 - 비공개 모임 인증코드 발급·재발급
     *
     * POST /api/groups/{groupId}/join-code
     *
     * 모임장만 발급 가능
     */
    @PostMapping
    public ResponseEntity<?> issueJoinCode(
            @PathVariable Long groupId,
            Authentication authentication
    ) {

        Long memberId = getMemberId(authentication);

        if (memberId == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }

        GroupJoinCodeIssueResponse response =
                issueService.prepareJoinCodeIssue(
                        groupId,
                        memberId
                );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    /**
     * F18 - 인증코드 검증
     *
     * POST /api/groups/{groupId}/join-code/verify
     *
     * 요청 예시:
     * {
     *   "joinCode": "123456"
     * }
     */
    @PostMapping("/verify")
    public ResponseEntity<?> verifyJoinCode(
            @PathVariable Long groupId,
            @RequestBody Map<String, String> request,
            Authentication authentication
    ) {

        Long memberId = getMemberId(authentication);

        if (memberId == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }

        String inputCode = request == null
                ? null
                : request.get("joinCode");

        JoinCodeVerifyResponse response =
                verifyService.verifyJoinCode(
                        groupId,
                        memberId,
                        inputCode
                );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

           /**
     * F18 - 현재 로그인 회원의 인증 상태 조회
     *
     * GET /api/groups/{groupId}/join-code/verification-state
     *
     * F5 새로고침 후 인증 상태 복원용
     */
    @GetMapping("/verification-state")
    public ResponseEntity<?> getVerificationState(
            @PathVariable Long groupId,
            Authentication authentication
    ) {
        Long memberId = getMemberId(authentication);

        if (memberId == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }

        JoinCodeVerifyResponse response =
                verifyService.checkVerificationState(
                        groupId,
                        memberId
                );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }
}
