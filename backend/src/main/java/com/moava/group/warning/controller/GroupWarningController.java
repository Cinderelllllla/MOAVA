package com.moava.group.warning.controller;

import com.moava.common.response.ApiResponse;
import com.moava.group.warning.dto.GroupWarningRequest;
import com.moava.group.warning.dto.GroupWarningResponse;
import com.moava.group.warning.service.GroupWarningException;
import com.moava.group.warning.service.GroupWarningService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * F30 모임 경고 및 강제 탈퇴.
 * <pre>
 * POST /api/v1/groups/{groupId}/members/{memberId}/warnings    경고 (모임장) body {reason}
 * POST /api/v1/groups/{groupId}/members/{memberId}/expulsion   강제 탈퇴 (모임장, 경고 2회 회원) body {reason}
 *  * GET  /api/v1/groups/{groupId}/warnings/me                    나의 누적 경고 (본인)
 * </pre>
 * SecurityConfig 가 /api/v1/groups/** 를 ROLE_MEMBER 로 막고 있다. 여기서도 회원 토큰인지 한 번 더 확인한다.
 */
@RestController
@RequestMapping("/api/v1/groups/{groupId}")
public class GroupWarningController {

    private final GroupWarningService service;

    public GroupWarningController(GroupWarningService service) {
        this.service = service;
    }

    @PostMapping("/members/memberId/warnings")
    public ApiResponse<GroupWarningResponse> warn(Authentication authentication, @PathVariable long groupId,
                                                  @PathVariable long memberId,
                                                  @RequestBody(required = false) GroupWarningRequest request) {
        return ApiResponse.ok(service.warn(leaderId(authentication), groupId, memberId, request), requestId());
    }

    @PostMapping("/members/memberId/expulsion")
    public ApiResponse<GroupWarningResponse> expel(Authentication authentication, @PathVariable long groupId,
                                                   @PathVariable long memberId,
                                                   @RequestBody(required = false) GroupWarningRequest request) {
        return ApiResponse.ok(service.expel(leaderId(authentication), groupId, memberId, request), requestId());
    }

    /** 나의 누적 경고 (이 모임의 ACTIVE 회원 본인) */
    @GetMapping("/warnings/me")
    public ApiResponse<GroupWarningResponse> myWarnings(Authentication authentication, @PathVariable long groupId) {
        return ApiResponse.ok(service.myWarnings(leaderId(authentication), groupId), requestId());
    }

    /** 회원 토큰만 허용 (관리자 토큰의 subject 는 ADMIN_ID 라 회원 번호로 쓰면 안 됨). 모임장 여부는 Service 에서 확인 */
    static long leaderId(Authentication authentication) {
        boolean member = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_MEMBER".equals(a.getAuthority()));
        if (!member) {
            throw new GroupWarningException(HttpStatus.FORBIDDEN, "MEMBER_ONLY", "회원만 이용할 수 있어요.");
        }
        return Long.parseLong(authentication.getName());
    }

    private static String requestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
}