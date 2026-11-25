package com.moava.support.controller;

import com.moava.common.response.ApiResponse;
import com.moava.support.dto.SupportReportDTO;
import com.moava.support.service.SupportReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * F40 고객센터 신고 접수 및 처리 API.
 * - 회원 S15 (/support): /api/v1/support/reports/** (ADMIN 접수/이용 차단 → '관리자는 신고 처리 메뉴를 이용해 주세요')
 * - 관리자 A08 (/admin/support): /api/v1/admin/support/reports/** (ROLE_ADMIN 전용)
 */
@RestController
@RequiredArgsConstructor
public class SupportReportController {

    private final SupportReportService supportReportService;

    // ==================== 회원 S15 API ====================

    /** S15 신고 대상 모임 선택 목록 (회원이 참여 중이거나 모임장인 모임) */
    @GetMapping("/api/v1/support/reports/groups")
    public ApiResponse<List<SupportReportDTO.GroupOptionItem>> getMyReportGroups(Authentication authentication) {
        Long memberId = requireMemberId(authentication);
        return ApiResponse.ok(supportReportService.getMemberGroups(memberId), requestId());
    }

    /** S15 회원 신고 접수 (ADMIN 접수 시도 → 403 '관리자는 신고 처리 메뉴를 이용해 주세요') */
    @PostMapping("/api/v1/support/reports")
    public ApiResponse<SupportReportDTO.CreateReportResponse> createReport(
            @RequestBody(required = false) SupportReportDTO.CreateReportRequest request,
            Authentication authentication
    ) {
        Long memberId = requireMemberId(authentication);
        return ApiResponse.ok(supportReportService.createReport(memberId, request), requestId());
    }

    /** S15 나의 신고 목록 조회 (본인 건만 조회·복호화) */
    @GetMapping("/api/v1/support/reports")
    public ApiResponse<SupportReportDTO.MemberReportListResponse> getMyReports(Authentication authentication) {
        Long memberId = requireMemberId(authentication);
        return ApiResponse.ok(supportReportService.getMyReports(memberId), requestId());
    }

    /** S15/A08 신고 단건 상세 조회 (회원은 본인 건만 열람 가능, 타인 건 403) */
    @GetMapping("/api/v1/support/reports/{reportId}")
    public ApiResponse<SupportReportDTO.ReportItem> getReportDetail(
            @PathVariable Long reportId,
            Authentication authentication
    ) {
        requireAuthenticated(authentication);
        if (hasRole(authentication, "ROLE_ADMIN")) {
            Long adminId = parsePrincipalId(authentication);
            return ApiResponse.ok(supportReportService.getAdminReportDetail(adminId, reportId), requestId());
        }
        if (hasRole(authentication, "ROLE_MEMBER")) {
            Long memberId = parsePrincipalId(authentication);
            return ApiResponse.ok(supportReportService.getMyReportDetail(memberId, reportId), requestId());
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
    }

    // ==================== 관리자 A08 API ====================

    /** A08 관리자 전체 신고 목록 조회 + 상태 필터 */
    @GetMapping("/api/v1/admin/support/reports")
    public ApiResponse<SupportReportDTO.AdminReportListResponse> getAdminReports(
            @RequestParam(defaultValue = "ALL") String status,
            Authentication authentication
    ) {
        Long adminId = requireAdminId(authentication);
        return ApiResponse.ok(supportReportService.getAdminReports(adminId, status), requestId());
    }

    /** A08 관리자 신고 단건 상세 조회 */
    @GetMapping("/api/v1/admin/support/reports/{reportId}")
    public ApiResponse<SupportReportDTO.ReportItem> getAdminReportDetail(
            @PathVariable Long reportId,
            Authentication authentication
    ) {
        Long adminId = requireAdminId(authentication);
        return ApiResponse.ok(supportReportService.getAdminReportDetail(adminId, reportId), requestId());
    }

    /** A08 관리자 신고 상태 변경 (처리 메모 필수) */
    @PutMapping("/api/v1/admin/support/reports/{reportId}/status")
    public ApiResponse<SupportReportDTO.StatusUpdateResponse> updateReportStatus(
            @PathVariable Long reportId,
            @RequestBody(required = false) SupportReportDTO.StatusUpdateRequest request,
            Authentication authentication
    ) {
        Long adminId = requireAdminId(authentication);
        return ApiResponse.ok(supportReportService.updateReportStatus(adminId, reportId, request), requestId());
    }

    // ==================== 인증/권한 도우미 ====================

    private static void requireAuthenticated(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
    }

    private static Long requireMemberId(Authentication authentication) {
        requireAuthenticated(authentication);
        if (hasRole(authentication, "ROLE_ADMIN")) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    SupportReportService.ADMIN_SUBMIT_FORBIDDEN_MESSAGE
            );
        }
        if (!hasRole(authentication, "ROLE_MEMBER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "회원만 이용할 수 있습니다.");
        }
        return parsePrincipalId(authentication);
    }

    private static Long requireAdminId(Authentication authentication) {
        requireAuthenticated(authentication);
        if (!hasRole(authentication, "ROLE_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "플랫폼 관리자만 접근할 수 있습니다.");
        }
        return parsePrincipalId(authentication);
    }

    private static boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }

    private static Long parsePrincipalId(Authentication authentication) {
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

    private static String requestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
