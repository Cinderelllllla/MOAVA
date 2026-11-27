package com.moava.group.audit.controller;

import com.moava.common.response.ApiResponse;
import com.moava.group.audit.dto.GroupAuditLogPageResponse;
import com.moava.group.audit.service.GroupAuditException;
import com.moava.group.audit.service.GroupAuditService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * F33 모임 운영 기록(감사 로그) 회원 조회 API.
 * <pre>
 * GET /api/v1/groups/{groupId}/audit-logs?eventType=...&amp;page=0&amp;size=8
 * </pre>
 * append-only 정책에 따라 로그 수정(PUT/PATCH)·삭제(DELETE) 엔드포인트는 제공하지 않는다.
 * SecurityConfig 가 /api/v1/groups/** 를 ROLE_MEMBER 로 보호하며, 컨트롤러에서도 회원 토큰을 재확인한다.
 */
@RestController
@RequestMapping("/api/v1/groups/{groupId}/audit-logs")
public class GroupAuditController {

    private final GroupAuditService service;

    public GroupAuditController(GroupAuditService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<GroupAuditLogPageResponse> getGroupAuditLogs(
            Authentication authentication,
            @PathVariable long groupId,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size
    ) {
        return ApiResponse.ok(
                service.getGroupAuditLogs(requireMemberId(authentication), groupId, eventType, page, size),
                requestId()
        );
    }

    static long requireMemberId(Authentication authentication) {
        boolean isMember = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_MEMBER".equals(a.getAuthority()));
        if (!isMember) {
            throw new GroupAuditException(HttpStatus.FORBIDDEN, "MEMBER_ONLY", "회원만 이용할 수 있어요.");
        }
        try {
            long id = Long.parseLong(authentication.getName());
            if (id <= 0) {
                throw new GroupAuditException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "로그인 정보가 올바르지 않아요.");
            }
            return id;
        } catch (NumberFormatException e) {
            throw new GroupAuditException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "로그인 정보가 올바르지 않아요.");
        }
    }

    private static String requestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
