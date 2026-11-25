package com.moava.references.controller;

import com.moava.common.response.ApiResponse;
import com.moava.references.dto.RefNoRequest;
import com.moava.references.dto.RefNoResponse;
import com.moava.references.dto.RefProofDto;
import com.moava.references.service.RefNoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * F24 거래 참조번호 관리 API (관리자, 화면 A05 /admin/references)
 * 응답은 API 명세서 v0.5 §2 공통 형식(ApiResponse)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/payment-proofs")
public class RefNoController {

    private final RefNoService refNoService;

    /**
     * 목록 — ?ref=UNASSIGNED(미지정, 기본값) / ASSIGNED(지정 완료)
     */
    @GetMapping
    public ApiResponse<List<RefProofDto>> getProofs(
            @RequestParam(defaultValue = "UNASSIGNED") String ref,
            Authentication authentication) {
        return ApiResponse.ok(refNoService.getProofs(loginAdminId(authentication), ref), requestId());
    }

    /**
     * 다음 참조번호 제안 (시퀀스 소모 없음)
     */
    @GetMapping("/ref-no/suggestion")
    public ApiResponse<RefNoResponse> suggestRefNo(Authentication authentication) {
        return ApiResponse.ok(refNoService.suggestRefNo(loginAdminId(authentication)), requestId());
    }

    /**
     * 참조번호 지정
     * - 요청 본문(JSON): {"refNo": "MOAVA-00012"} 또는 {"refNo": null}(서버가 다음 번호로 지정)
     */
    @PatchMapping("/{proofId}/ref-no")
    public ApiResponse<RefNoResponse> assignRefNo(
            @PathVariable Long proofId,
            @RequestBody(required = false) RefNoRequest req,
            Authentication authentication) {
        return ApiResponse.ok(refNoService.assignRefNo(loginAdminId(authentication), proofId, req), requestId());
    }

    /**
     * 참조번호 정정
     * - 요청 본문(JSON): {"refNo": "MOAVA-00015", "reason": "정정 사유"}
     */
    @PatchMapping("/{proofId}/ref-no/correction")
    public ApiResponse<RefNoResponse> correctRefNo(
            @PathVariable Long proofId,
            @RequestBody RefNoRequest req,
            Authentication authentication) {
        return ApiResponse.ok(refNoService.correctRefNo(loginAdminId(authentication), proofId, req), requestId());
    }

    // ---------- 로그인 관리자 ----------

    /**
     * JWT에서 ADMIN_ID 꺼내기 — 관리자 로그인(POST /api/auth/admin/login) 토큰: subject=ADMIN_ID, 권한 ROLE_ADMIN
     * 관리자 전용 확인은 여기(ROLE_ADMIN)와 서비스(ACTIVE 관리자)에서 함 — SecurityConfig URL 규칙에 기대지 않음
     * (ACTIVE 관리자인지는 RefNoService.checkAdmin에서 DB로 확인)
     */
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