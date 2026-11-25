package com.moava.admin.groups.controller;

import com.moava.admin.groups.dto.AdminGroupAuditDTO;
import com.moava.admin.groups.service.AdminGroupAuditService;
import com.moava.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * F38 계모임 관리 및 이력 감사 API (관리자 전용, 화면 A02 /admin/groups).
 * - 모임 목록 / 관리자 상세 패널(조회 전용, 가입 승인·증빙 승인 개입 불가)
 * - AUDIT_LOG 필터 3종 + v3.1 관리자 필터(관리자 조치만, 담당 관리자별) + 페이지당 8건 페이징
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminGroupAuditController {

    private final AdminGroupAuditService adminGroupAuditService;

    /** A02 전체 계모임 목록 조회 */
    @GetMapping("/groups")
    public ApiResponse<List<AdminGroupAuditDTO.GroupSummaryItem>> getGroups(Authentication authentication) {
        Long adminId = requireAdminId(authentication);
        return ApiResponse.ok(adminGroupAuditService.getGroups(adminId), requestId());
    }

    /** A02 관리자 상세 패널 조회 (규칙, 가입 요청·증빙·탈퇴·미납 건수 — 조회 전용) */
    @GetMapping("/groups/{groupId}")
    public ApiResponse<AdminGroupAuditDTO.GroupDetailResponse> getGroupDetail(
            @PathVariable Long groupId,
            Authentication authentication
    ) {
        Long adminId = requireAdminId(authentication);
        return ApiResponse.ok(adminGroupAuditService.getGroupDetail(adminId, groupId), requestId());
    }

    /**
     * A02 감사 이력 필터·페이징 조회 (기본 페이지당 8건).
     * - groupId: 모임 선택
     * - eventType: 이벤트 유형
     * - reviewStatus: ALL(전체) | REVIEW(_REQUEST/_DETECT 접미사) | DONE(완료)
     * - adminOnly: (v3.1) true면 ACTOR_ADMIN_ID IS NOT NULL
     * - actorAdminId: (v3.1) 담당 관리자별 조회
     * - page: 0-based 페이지 번호 (기본 0)
     * - size: 페이지당 건수 (기본 8)
     */
    @GetMapping("/audit-logs")
    public ApiResponse<AdminGroupAuditDTO.AuditLogPageResponse> getAuditLogs(
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "ALL") String reviewStatus,
            @RequestParam(defaultValue = "false") boolean adminOnly,
            @RequestParam(required = false) Long actorAdminId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size,
            Authentication authentication
    ) {
        Long adminId = requireAdminId(authentication);
        return ApiResponse.ok(
                adminGroupAuditService.getAuditLogs(
                        adminId, groupId, eventType, reviewStatus, adminOnly, actorAdminId, page, size
                ),
                requestId()
        );
    }

    /** A02 감사 이력 필터 메타 옵션 (이벤트 유형 목록 + 담당 관리자 목록) */
    @GetMapping("/audit-logs/meta")
    public ApiResponse<AdminGroupAuditDTO.AuditFilterOptionsResponse> getAuditFilterOptions(
            Authentication authentication
    ) {
        Long adminId = requireAdminId(authentication);
        return ApiResponse.ok(adminGroupAuditService.getFilterOptions(adminId), requestId());
    }

    private static Long requireAdminId(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        if (!isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "플랫폼 관리자만 접근할 수 있습니다.");
        }
        try {
            long adminId = Long.parseLong(authentication.getName());
            if (adminId <= 0) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인 정보가 올바르지 않습니다.");
            }
            return adminId;
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인 정보가 올바르지 않습니다.");
        }
    }

    private static String requestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
