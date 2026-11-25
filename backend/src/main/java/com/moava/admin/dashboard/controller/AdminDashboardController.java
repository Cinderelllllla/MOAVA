// 1. 대시보드 조회 컨트롤러 (상시 활성)
package com.moava.admin.dashboard.controller;

import com.moava.admin.dashboard.dto.AdminDashboardDTO;
import com.moava.admin.dashboard.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    @GetMapping
    public ResponseEntity<AdminDashboardDTO.DashboardResponse> getDashboard(Authentication authentication) {
        Long adminId = requireAdminId(authentication);
        return ResponseEntity.ok(dashboardService.getDashboardSummary(adminId));
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
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인 정보가 올바르지 않습니다.");
        }
    }
}