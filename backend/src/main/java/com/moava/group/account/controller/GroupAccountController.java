package com.moava.group.account.controller;

import com.moava.group.account.dto.GroupAccountResponse;
import com.moava.group.account.dto.GroupAccountVerifyResponse;
import com.moava.group.account.service.GroupAccountService;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/accounts")
public class GroupAccountController {

    private final GroupAccountService groupAccountService;

    public GroupAccountController(
            GroupAccountService groupAccountService
    ) {
        this.groupAccountService = groupAccountService;
    }

    // F19 - 현재 모임 계좌 조회
    @GetMapping("/current")
    public ResponseEntity<GroupAccountResponse> getCurrentAccount(
            @PathVariable("groupId") Long groupId,
            Authentication authentication
    ) {

        Long memberId = resolveMemberId(authentication);

        GroupAccountResponse response =
                groupAccountService.getCurrentAccount(
                        groupId,
                        memberId
                );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .body(response);
    }

    // F19 - 현재 모임 계좌 소유 인증
    @PostMapping("/verify")
    public ResponseEntity<GroupAccountVerifyResponse> verifyAccount(
            @PathVariable("groupId") Long groupId,
            Authentication authentication
    ) {

        Long memberId =
                resolveMemberId(authentication);

        GroupAccountVerifyResponse response =
                groupAccountService.verifyCurrentAccount(
                        groupId,
                        memberId
                );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .body(response);
    }

    // F19 - 로그인한 일반 회원의 ID 추출
    private Long resolveMemberId(
            Authentication authentication
    ) {

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || authentication.getName() == null
                || authentication.getName().isBlank()) {

            throw new SecurityException(
                    "로그인이 필요합니다."
            );
        }

        boolean isMember =
                authentication.getAuthorities() != null
                && authentication.getAuthorities()
                        .stream()
                        .anyMatch(authority ->
                                "ROLE_MEMBER".equals(
                                        authority.getAuthority()
                                )
                        );

        if (!isMember) {

            throw new SecurityException(
                    "일반 회원만 이용할 수 있습니다."
            );
        }

        try {

            Long memberId =
                    Long.valueOf(authentication.getName());

            if (memberId <= 0) {

                throw new SecurityException(
                        "로그인이 필요합니다."
                );
            }

            return memberId;

        } catch (NumberFormatException e) {

            throw new SecurityException(
                    "로그인이 필요합니다."
            );
        }
    }
}
