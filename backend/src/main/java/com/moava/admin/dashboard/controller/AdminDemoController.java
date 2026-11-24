// 2. 시연 전용 컨트롤러 (운영 프로필 제외)
package com.moava.admin.dashboard.controller;

import com.moava.admin.dashboard.dto.AdminDashboardDTO;
import com.moava.admin.dashboard.service.AdminDemoService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Profile("!prod")
@RestController
@RequestMapping("/api/v1/admin/demo")
@RequiredArgsConstructor
public class AdminDemoController {

    private final AdminDemoService demoService;

    // 시연용 테스트 회원 5명 생성 (이름·이메일 접미 번호 중복 방지)
    @PostMapping("/test-members")
    public ResponseEntity<AdminDashboardDTO.TestMembersResponse> createTestMembers(Authentication authentication) {
        Long adminId = requireAdminId(authentication);
        return ResponseEntity.ok(demoService.createFiveTestMembers(adminId));
    }

    // 데모 데이터 일괄 초기화 (회원·모임·정책 데이터 포함)
    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> resetDemoData(Authentication authentication) {
        Long adminId = requireAdminId(authentication);
        demoService.resetAllDemoData(adminId);
        return ResponseEntity.ok(Map.of("message", "회원·모임·정책 시연 데이터를 모두 초기화했습니다."));
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