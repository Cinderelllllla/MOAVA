package com.moava.group.rules.controller;

import com.moava.common.response.ApiResponse;
import com.moava.group.rules.dto.GroupRulesResponse;
import com.moava.group.rules.dto.GroupRulesUpdateRequest;
import com.moava.group.rules.service.GroupRulesException;
import com.moava.group.rules.service.GroupRulesService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * F31 운영 규칙.
 * <pre>
 * GET /api/v1/groups/{groupId}/rules   최신 규칙 (모임의 ACTIVE 회원)
 * PUT /api/v1/groups/{groupId}/rules   규칙 수정 (모임장) body {rulesTitle, rulesBody}
 * </pre>
 * SecurityConfig 가 /api/v1/groups/** 를 ROLE_MEMBER 로 막고 있다. 여기서도 회원 토큰인지 한 번 더 확인한다.
 */
@RestController
@RequestMapping("/api/v1/groups/{groupId}/rules")
public class GroupRulesController {

    private final GroupRulesService service;

    public GroupRulesController(GroupRulesService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<GroupRulesResponse> get(Authentication authentication, @PathVariable long groupId) {
        return ApiResponse.ok(service.get(memberId(authentication), groupId), requestId());
    }

    @PutMapping
    public ApiResponse<GroupRulesResponse> update(Authentication authentication, @PathVariable long groupId,
                                                  @RequestBody(required = false) GroupRulesUpdateRequest request) {
        return ApiResponse.ok(service.update(memberId(authentication), groupId, request), requestId());
    }

    /** 회원 토큰만 허용 (관리자 토큰의 subject 는 ADMIN_ID 라 회원 번호로 쓰면 안 됨) */
    static long memberId(Authentication authentication) {
        boolean member = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_MEMBER".equals(a.getAuthority()));
        if (!member) {
            throw new GroupRulesException(HttpStatus.FORBIDDEN, "MEMBER_ONLY", "회원만 이용할 수 있어요.");
        }
        return Long.parseLong(authentication.getName());
    }

    private static String requestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
