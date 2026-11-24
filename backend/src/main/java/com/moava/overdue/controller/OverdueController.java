package com.moava.overdue.controller;

import com.moava.common.response.ApiResponse;
import com.moava.overdue.dto.OverdueActionRequest;
import com.moava.overdue.dto.OverdueCaseDto;
import com.moava.overdue.service.OverdueAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * F26 미납 관리 API (관리자, 화면 A06 /admin/overdue)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/overdue-cases")
public class OverdueController {

    private final OverdueAdminService overdueAdminService;

    /** A06 목록 — ?status=OPEN(미해소, 기본값) / RESOLVED(해소) / ALL */
    @GetMapping
    public ApiResponse<List<OverdueCaseDto>> getCases(
            @RequestParam(defaultValue = "OPEN") String status,
            Authentication authentication) {
        return ApiResponse.ok(overdueAdminService.getCases(loginAdminId(authentication), status), requestId());
    }
    /// A06 조치 — {"action": "WARN" | "CHAT\_RESTRICT" | "CHAT\_RELEASE" | "ORDER\_REVIEW", "reason": "..."}
    /// 처리 후 바뀐 미납 건을 돌려줌 (화면에서 상태·제한 여부를 바로 갱신)
    @PostMapping("/{overdueCaseId}/actions")
    public ApiResponse<OverdueCaseDto> applyAction(
            @PathVariable Long overdueCaseId,
            @RequestBody OverdueActionRequest req,
            Authentication authentication) {
        return ApiResponse.ok(
                overdueAdminService.applyAction(loginAdminId(authentication), overdueCaseId, req), requestId());
    }

    // ---------- 로그인 관리자 (RefNoController와 같음) ----------

    private static Long loginAdminId(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 이용할 수 있습니다.");
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인 정보가 올바르지 않습니다.");
        }
    }

    private static String requestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
}