
package com.moava.group.create.controller;

import com.moava.group.create.dto.GroupJoinRequestResponse;
import com.moava.group.create.service.GroupJoinRequestService;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/groups/{groupId}/join-requests")
public class GroupJoinRequestController {

    private final GroupJoinRequestService service;

    public GroupJoinRequestController(
            GroupJoinRequestService service
    ) {
        this.service = service;
    }

    // ==================================================
    // F16 - 모임 가입 신청
    // POST /api/groups/{groupId}/join-requests
    // ==================================================
    @PostMapping
    public ResponseEntity<?> requestJoin(
            @PathVariable Long groupId,
            @RequestBody Map<String, String> request,
            Authentication authentication
    ) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "message", "로그인이 필요합니다."
                    ));
        }

        Long memberId;

        try {
            memberId = Long.valueOf(
                    authentication.getName()
            );
        } catch (NumberFormatException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "message", "로그인이 필요합니다."
                    ));
        }

        try {
            String via = request == null
                    ? null
                    : request.get("via");

            GroupJoinRequestResponse response =
                    service.requestJoin(
                            groupId,
                            memberId,
                            via
                    );

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .cacheControl(CacheControl.noStore())
                    .body(response);

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

    // ==================================================
    // F16 - 모임장 가입 신청 목록 조회
    // GET /api/groups/{groupId}/join-requests
    // ==================================================
    @GetMapping
    public ResponseEntity<?> getPendingRequests(
            @PathVariable Long groupId,
            Authentication authentication
    ) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "message", "로그인이 필요합니다."
                    ));
        }

        Long ownerId;

        try {
            ownerId = Long.valueOf(
                    authentication.getName()
            );
        } catch (NumberFormatException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "message", "로그인이 필요합니다."
                    ));
        }

        try {
            return ResponseEntity
                    .ok()
                    .cacheControl(CacheControl.noStore())
                    .body(
                            service.getPendingRequests(
                                    groupId,
                                    ownerId
                            )
                    );

        } catch (SecurityException e) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "message", e.getMessage()
                    ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of(
                            "message", e.getMessage()
                    ));
        }
    }


    // ==================================================
    // F16 - 모임장 가입 신청 승인
    // PATCH /api/groups/{groupId}/join-requests/{requestId}/approve
    // ==================================================
    @PatchMapping("/{requestId}/approve")
    public ResponseEntity<?> approveJoinRequest(
            @PathVariable Long groupId,
            @PathVariable Long requestId,
            Authentication authentication
    ) {
        // 1. 로그인 여부 확인
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "message", "로그인이 필요합니다."
                    ));
        }

        // 2. JWT에서 로그인 회원 ID 추출
        Long ownerId;

        try {
            ownerId = Long.valueOf(
                    authentication.getName()
            );
        } catch (NumberFormatException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "message", "로그인이 필요합니다."
                    ));
        }

        try {
            // 3. Service에서 권한 및 가입 승인 처리
            service.approveJoinRequest(
                    groupId,
                    requestId,
                    ownerId
            );

            // 4. 승인 성공 응답
            return ResponseEntity
                    .ok()
                    .cacheControl(CacheControl.noStore())
                    .body(Map.of(
                            "joinRequestId", requestId,
                            "groupId", groupId,
                            "status", "APPROVED",
                            "message", "가입 신청을 승인했습니다."
                    ));

        } catch (SecurityException e) {
            // 모임장 권한 없음
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "message", e.getMessage()
                    ));

        } catch (IllegalArgumentException e) {
            // 잘못된 요청 또는 존재하지 않는 신청
            return ResponseEntity
                    .badRequest()
                    .body(Map.of(
                            "message", e.getMessage()
                    ));

        } catch (IllegalStateException e) {
            // 이미 처리됨, 정원 초과, 중복 모임원 등
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message", e.getMessage()
                    ));
        }
    }


    // ==================================================
    // F16 - 모임장 가입 신청 거절
    // PATCH /api/groups/{groupId}/join-requests/{requestId}/reject
    // ==================================================
    @PatchMapping("/{requestId}/reject")
    public ResponseEntity<?> rejectJoinRequest(
            @PathVariable Long groupId,
            @PathVariable Long requestId,
            Authentication authentication
    ) {
        // 1. 로그인 여부 확인
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "message", "로그인이 필요합니다."
                    ));
        }

        // 2. JWT에서 로그인 회원 ID 추출
        Long ownerId;

        try {
            ownerId = Long.valueOf(
                    authentication.getName()
            );
        } catch (NumberFormatException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "message", "로그인이 필요합니다."
                    ));
        }

        try {
            // 3. Service 거절 처리
            service.rejectJoinRequest(
                    groupId,
                    requestId,
                    ownerId
            );

            // 4. 거절 성공 응답
            return ResponseEntity
                    .ok()
                    .cacheControl(CacheControl.noStore())
                    .body(Map.of(
                            "joinRequestId", requestId,
                            "groupId", groupId,
                            "status", "REJECTED",
                            "message", "가입 신청을 거절했습니다."
                    ));

        } catch (SecurityException e) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "message", e.getMessage()
                    ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of(
                            "message", e.getMessage()
                    ));

        } catch (IllegalStateException e) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message", e.getMessage()
                    ));
        }
    }


}
