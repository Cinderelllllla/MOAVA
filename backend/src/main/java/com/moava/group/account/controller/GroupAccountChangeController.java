package com.moava.group.account.controller;

import com.moava.group.account.dto.GroupAccountChangeRequest;
import com.moava.group.account.dto.GroupAccountChangeResponse;
import com.moava.group.account.service.GroupAccountChangeService;
import com.moava.group.account.dto.GroupAccountConsentStatusResponse;
import com.moava.group.account.dto.GroupAccountConsentRequest;
import com.moava.group.account.dto.GroupAccountConsentResponse;
import com.moava.group.account.dto.GroupAccountChangeCloseResponse;
import com.moava.group.account.dto.GroupAccountChangeVerifyResponse;
import com.moava.group.account.service.GroupAccountChangeVerificationService;
import com.moava.group.account.dto.GroupAccountChangeReadinessResponse;
import com.moava.group.account.dto.GroupAccountMockLinkResponse;
import com.moava.group.account.service.GroupAccountMockLinkService;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/account-changes")
public class GroupAccountChangeController {

    private final GroupAccountChangeService service;
    private final GroupAccountChangeVerificationService verificationService;
    private final GroupAccountMockLinkService mockLinkService;

    public GroupAccountChangeController(
            GroupAccountChangeService service,
            GroupAccountChangeVerificationService verificationService,
            GroupAccountMockLinkService mockLinkService
    ) {
        this.service = service;
        this.verificationService = verificationService;
        this.mockLinkService = mockLinkService;
    }

    // F20 - 모임 계좌 변경 신청
    @PostMapping
    public ResponseEntity<GroupAccountChangeResponse> requestChange(
            @PathVariable("groupId") Long groupId,
            @RequestBody GroupAccountChangeRequest request,
            Authentication authentication
    ) {

        Long memberId = resolveMemberId(authentication);

        GroupAccountChangeResponse response =
                service.requestAccountChange(
                        groupId,
                        memberId,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .body(response);
    }


    // F20 - 회원 동의 현황 조회
    @GetMapping("/consents")
    public ResponseEntity<GroupAccountConsentStatusResponse>
            getConsentStatus(
                    @PathVariable("groupId") Long groupId,
                    Authentication authentication
            ) {

        Long memberId = resolveMemberId(authentication);

        GroupAccountConsentStatusResponse response =
                service.getConsentStatus(groupId, memberId);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .body(response);
    }

    // F20 - 회원 계좌 변경 동의·거절
    @PostMapping("/{accountId}/consents")
    public ResponseEntity<GroupAccountConsentResponse>
            respondToConsent(
                    @PathVariable("groupId") Long groupId,
                    @PathVariable("accountId") Long accountId,
                    @RequestBody GroupAccountConsentRequest request,
                    Authentication authentication
            ) {

        Long memberId = resolveMemberId(authentication);

        GroupAccountConsentResponse response =
                service.respondToConsent(
                        groupId,
                        accountId,
                        memberId,
                        request
                );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .body(response);
    }


    // F20 - 모임장 계좌 변경 신청 취소
    @DeleteMapping("/{accountId}")
    public ResponseEntity<GroupAccountChangeCloseResponse>
            cancelChange(
                    @PathVariable("groupId") Long groupId,
                    @PathVariable("accountId") Long accountId,
                    Authentication authentication
            ) {

        Long memberId = resolveMemberId(authentication);

        GroupAccountChangeCloseResponse response =
                service.cancelChange(
                        groupId, accountId, memberId
                );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .body(response);
    }

    // F20 - 변경 신청 계좌 소유 재인증
    @PostMapping("/{accountId}/verify")
    public ResponseEntity<GroupAccountChangeVerifyResponse>
            verifyPendingAccount(
                    @PathVariable("groupId") Long groupId,
                    @PathVariable("accountId") Long accountId,
                    Authentication authentication
            ) {

        Long memberId = resolveMemberId(authentication);

        GroupAccountChangeVerifyResponse response =
                verificationService.verify(
                        groupId,
                        accountId,
                        memberId
                );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .body(response);
    }

    // F20 - 변경 확정 사전 검증 (읽기 전용)
    @GetMapping("/{accountId}/readiness")
    public ResponseEntity<GroupAccountChangeReadinessResponse>
            checkReadiness(
                    @PathVariable("groupId") Long groupId,
                    @PathVariable("accountId") Long accountId,
                    Authentication authentication
            ) {

        Long memberId = resolveMemberId(authentication);

        GroupAccountChangeReadinessResponse response =
                service.checkReadiness(
                        groupId, accountId, memberId
                );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .body(response);
    }

    // F20 - 모의 계좌 연결 상태 조회 (읽기 전용)
    @GetMapping("/{accountId}/mock-links")
    public ResponseEntity<GroupAccountMockLinkResponse>
            checkMockLinks(
                    @PathVariable("groupId") Long groupId,
                    @PathVariable("accountId") Long accountId,
                    Authentication authentication
            ) {

        Long memberId = resolveMemberId(authentication);

        GroupAccountMockLinkResponse response =
                mockLinkService.checkLinks(
                        groupId, accountId, memberId
                );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .body(response);
    }

    // F19와 동일한 로그인 회원 검증 방식
    private Long resolveMemberId(
            Authentication authentication
    ) {

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || authentication.getName() == null
                || authentication.getName().isBlank()) {

            throw new SecurityException("로그인이 필요합니다.");
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
                throw new SecurityException("로그인이 필요합니다.");
            }

            return memberId;

        } catch (NumberFormatException e) {
            throw new SecurityException("로그인이 필요합니다.");
        }
    }
}
