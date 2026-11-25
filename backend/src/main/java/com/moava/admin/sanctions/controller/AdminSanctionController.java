package com.moava.admin.sanctions.controller;

import com.moava.admin.sanctions.dto.AdminSanctionDTO;
import com.moava.admin.sanctions.service.AdminSanctionService;
import com.moava.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * F39 플랫폼 가입 제한 설정·해제 및 경고 현황 API (관리자 전용, 화면 A03 /admin/sanctions).
 * - 제재 대상 후보 / 현재 가입 제한 목록 / 가입 제한 설정(BAN) / 가입 제한 해제(BAN_RELEASE)
 * - 모임별 회원 경고 현황(n/2회, 2회 = 강제 탈퇴 검토 대상)
 * - 경고·강제 탈퇴 이력 검색(회원·모임명)·유형 필터·8건 페이징
 */
@RestController
@RequestMapping("/api/v1/admin/sanctions")
@RequiredArgsConstructor
public class AdminSanctionController {

    private final AdminSanctionService adminSanctionService;

    /** A03 종합 조회 (제재 대상 후보 + 현재 가입 제한 목록 + 모임별 회원 경고 현황) */
    @GetMapping("/overview")
    public ApiResponse<AdminSanctionDTO.SanctionOverviewResponse> getOverview(Authentication authentication) {
        Long adminId = requireAdminId(authentication);
        return ApiResponse.ok(adminSanctionService.getOverview(adminId), requestId());
    }

    /** A03 제재 대상 후보 회원 목록 (현재 가입 제한 중 제외) */
    @GetMapping("/candidates")
    public ApiResponse<List<AdminSanctionDTO.BanCandidateItem>> getBanCandidates(Authentication authentication) {
        Long adminId = requireAdminId(authentication);
        return ApiResponse.ok(adminSanctionService.getBanCandidates(adminId), requestId());
    }

    /** A03 현재 가입 제한 회원 목록 */
    @GetMapping("/bans")
    public ApiResponse<List<AdminSanctionDTO.BannedMemberItem>> getBannedMembers(Authentication authentication) {
        Long adminId = requireAdminId(authentication);
        return ApiResponse.ok(adminSanctionService.getBannedMembers(adminId), requestId());
    }

    /** A03 플랫폼 가입 제한 설정 (MEMBER.BANNED_YN='Y', SANCTION(BAN) append, 알림, 감사 로그) */
    @PostMapping("/bans")
    public ApiResponse<AdminSanctionDTO.BanActionResponse> banMember(
            @RequestBody(required = false) AdminSanctionDTO.BanRequest request,
            Authentication authentication
    ) {
        Long adminId = requireAdminId(authentication);
        return ApiResponse.ok(adminSanctionService.banMember(adminId, request), requestId());
    }

    /** A03 플랫폼 가입 제한 해제 (MEMBER.BANNED_YN='N', SANCTION(BAN_RELEASE) append, 이전 기록 보존, 알림, 감사 로그) */
    @PostMapping("/bans/{memberId}/release")
    public ApiResponse<AdminSanctionDTO.BanActionResponse> releaseBan(
            @PathVariable Long memberId,
            @RequestBody(required = false) AdminSanctionDTO.BanReleaseRequest request,
            Authentication authentication
    ) {
        Long adminId = requireAdminId(authentication);
        return ApiResponse.ok(adminSanctionService.releaseBan(adminId, memberId, request), requestId());
    }

    /** A03 모임별 회원 경고 현황 (n/2회, 2회 = 강제 탈퇴 검토 대상) */
    @GetMapping("/warnings")
    public ApiResponse<List<AdminSanctionDTO.MemberWarningItem>> getMemberWarnings(Authentication authentication) {
        Long adminId = requireAdminId(authentication);
        return ApiResponse.ok(adminSanctionService.getMemberWarnings(adminId), requestId());
    }

    /**
     * A03 경고·강제 탈퇴 이력 검색·유형 필터·페이징 조회 (기본 8건).
     * - keyword: 회원명 또는 모임명 검색어
     * - type: ALL(전체) | WARN(경고) | EXPEL(강제 탈퇴)
     * - page: 0-based 페이지 번호 (기본 0)
     * - size: 페이지당 건수 (기본 8)
     */
    @GetMapping("/history")
    public ApiResponse<AdminSanctionDTO.SanctionHistoryPageResponse> getWarningExpelHistory(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "ALL") String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size,
            Authentication authentication
    ) {
        Long adminId = requireAdminId(authentication);
        return ApiResponse.ok(
                adminSanctionService.getWarningExpelHistory(adminId, keyword, type, page, size),
                requestId()
        );
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
