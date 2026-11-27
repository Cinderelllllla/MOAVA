package com.moava.group.transfer.controller;

import com.moava.group.transfer.dto.LeaderTransferDtos.*;
import com.moava.group.transfer.service.LeaderTransferService;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/leader-transfers")
public class LeaderTransferController {

    private final LeaderTransferService service;

    public LeaderTransferController(
            LeaderTransferService service
    ) {
        this.service = service;
    }

    // JWT에서 실제 로그인 회원 ID 확인
    private Long member(Authentication authentication) {

        if (
                authentication == null ||
                !authentication.isAuthenticated() ||
                authentication instanceof AnonymousAuthenticationToken
        ) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        boolean isMember =
                authentication.getAuthorities() != null
                && authentication.getAuthorities()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch("ROLE_MEMBER"::equals);

        if (!isMember) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "일반 회원만 이용할 수 있습니다."
            );
        }

        try {
            long id = Long.parseLong(
                    authentication.getName()
            );

            if (id > 0) {
                return id;
            }

        } catch (NumberFormatException ignored) {
            // 올바르지 않은 JWT subject
        }

        throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "로그인 정보가 올바르지 않습니다."
        );
    }

    private ResponseEntity<StatusResponse> ok(
            StatusResponse response
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.PRAGMA,
                        "no-cache"
                )
                .header(
                        "X-Content-Type-Options",
                        "nosniff"
                )
                .body(response);
    }

    // 승계 현황 조회
    @GetMapping
    public ResponseEntity<StatusResponse> status(
            @PathVariable Long groupId,
            Authentication authentication
    ) {
        return ok(
                service.status(
                        groupId,
                        member(authentication)
                )
        );
    }

    // 모임장 승계 신청
    @PostMapping
    public ResponseEntity<StatusResponse> create(
            @PathVariable Long groupId,
            Authentication authentication,
            @RequestBody CreateRequest request
    ) {
        return ok(
                service.request(
                        groupId,
                        member(authentication),
                        request
                )
        );
    }

    // 대상 회원 수락 / 거절
    @PostMapping("/{transferId}/decision")
    public ResponseEntity<StatusResponse> decision(
            @PathVariable Long groupId,
            @PathVariable Long transferId,
            Authentication authentication,
            @RequestBody DecisionRequest request
    ) {
        return ok(
                service.decide(
                        groupId,
                        transferId,
                        member(authentication),
                        request
                )
        );
    }

    // 회원 전원 동의
    @PostMapping("/{transferId}/consents")
    public ResponseEntity<StatusResponse> consent(
            @PathVariable Long groupId,
            @PathVariable Long transferId,
            Authentication authentication,
            @RequestBody ConsentRequest request
    ) {
        return ok(
                service.consent(
                        groupId,
                        transferId,
                        member(authentication),
                        request
                )
        );
    }

    // 기존 모임장 최종 확정
    @PostMapping("/{transferId}/confirm")
    public ResponseEntity<StatusResponse> confirm(
            @PathVariable Long groupId,
            @PathVariable Long transferId,
            Authentication authentication
    ) {
        return ok(
                service.confirm(
                        groupId,
                        transferId,
                        member(authentication)
                )
        );
    }

    // 기존 모임장 신청 취소
    @PostMapping("/{transferId}/cancel")
    public ResponseEntity<StatusResponse> cancel(
            @PathVariable Long groupId,
            @PathVariable Long transferId,
            Authentication authentication
    ) {
        return ok(
                service.cancel(
                        groupId,
                        transferId,
                        member(authentication)
                )
        );
    }
}
