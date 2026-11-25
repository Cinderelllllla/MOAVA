package com.moava.group.procedures.controller;

import com.moava.group.procedures.dto.DissolutionDTO;
import com.moava.group.procedures.service.DissolutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class DissolutionController {

    private final DissolutionService dissolutionService;

    // [S10 ⑤] 특정 모임의 해산 상태 배지 및 상세 조회
    @GetMapping("/api/v1/groups/{groupId}/dissolution")
    public ResponseEntity<DissolutionDTO.StatusResponse> getGroupDissolution(
            @PathVariable Long groupId, Authentication authentication) {
        DissolutionDTO.StatusResponse status =
                dissolutionService.getGroupDissolutionStatus(groupId, requireMemberId(authentication));
        if (status == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(status);
    }

    // [S10 ⑤ - 신청] 모임장의 계모임 해산 신청
    @PostMapping("/api/v1/groups/{groupId}/dissolution")
    public ResponseEntity<Map<String, String>> requestDissolution(
            @PathVariable Long groupId,
            Authentication authentication,
            @RequestBody DissolutionDTO.RequestForm form) {
        String message = dissolutionService.requestDissolution(groupId, requireMemberId(authentication), form);
        return ResponseEntity.ok(Map.of("message", message));
    }

    // [A04] 관리자용 해산 신청 목록 및 종료된 모임 기록 조회 (status=PENDING, APPROVED, REJECTED, 미전달 시 전체)
    @GetMapping("/api/v1/admin/dissolutions")
    public ResponseEntity<List<DissolutionDTO.StatusResponse>> getAdminDissolutionList(
            Authentication authentication,
            @RequestParam(required = false) String status) {
        Long adminId = requireAdminId(authentication);
        return ResponseEntity.ok(dissolutionService.getAdminDissolutionList(adminId, status));
    }

    // [A04 - 승인] ADMIN의 미종결 증빙 검증 후 해산 최종 승인 (admin.approveDissolve(groupId) 매핑)
    @PostMapping("/api/v1/admin/groups/{groupId}/dissolution/approve")
    public ResponseEntity<Map<String, String>> approveDissolution(
            @PathVariable Long groupId,
            Authentication authentication,
            @RequestBody(required = false) DissolutionDTO.AdminDecisionForm form) {
        Long adminId = requireAdminId(authentication);
        String message = dissolutionService.approveDissolutionByAdmin(groupId, adminId);
        return ResponseEntity.ok(Map.of("message", message));
    }

    // [A04 - 반려] ADMIN의 해산 신청 반려 (admin.rejectDissolve(groupId, reason) 매핑)
    @PostMapping("/api/v1/admin/groups/{groupId}/dissolution/reject")
    public ResponseEntity<Map<String, String>> rejectDissolution(
            @PathVariable Long groupId,
            Authentication authentication,
            @RequestBody(required = false) DissolutionDTO.AdminDecisionForm form) {
        Long adminId = requireAdminId(authentication);
        String reason = form == null ? null : form.getRejectReason();
        String message = dissolutionService.rejectDissolutionByAdmin(groupId, adminId, reason);
        return ResponseEntity.ok(Map.of("message", message));
    }

    private static Long requireMemberId(Authentication authentication) {
        requireAuthenticated(authentication);
        if (!hasAuthority(authentication, "ROLE_MEMBER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "일반 회원만 이용할 수 있습니다.");
        }
        return parseSubjectId(authentication);
    }

    private static Long requireAdminId(Authentication authentication) {
        requireAuthenticated(authentication);
        if (!hasAuthority(authentication, "ROLE_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "플랫폼 관리자만 해산을 처리할 수 있습니다.");
        }
        return parseSubjectId(authentication);
    }

    private static void requireAuthenticated(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
    }

    private static boolean hasAuthority(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }

    private static Long parseSubjectId(Authentication authentication) {
        try {
            long id = Long.parseLong(authentication.getName());
            if (id <= 0) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인 정보가 올바르지 않습니다.");
            }
            return id;
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인 정보가 올바르지 않습니다.");
        }
    }
}