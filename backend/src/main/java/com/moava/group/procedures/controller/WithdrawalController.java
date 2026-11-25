package com.moava.group.procedures.controller;

import com.moava.group.procedures.dto.WithdrawalDTO;
import com.moava.group.procedures.service.WithdrawalService;
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
@RequestMapping("/api/v1/groups/{groupId}/withdrawals")
public class WithdrawalController {

    private final WithdrawalService withdrawalService;

    public WithdrawalController(WithdrawalService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    // 0. 회원 탈퇴 검토 및 정산안 동의 현황 목록 조회
    @GetMapping
    public ResponseEntity<List<WithdrawalDTO.StatusResponse>> getWithdrawalList(
            @PathVariable Long groupId, Authentication authentication) {
        return ResponseEntity.ok(withdrawalService.getWithdrawalList(groupId, requireMemberId(authentication)));
    }

    // 1. [신청] 일반 회원의 중도 탈퇴 신청 (SETTLEMENT_PENDING)
    @PostMapping
    public ResponseEntity requestWithdrawal(
            @PathVariable Long groupId,
            Authentication authentication,
            @RequestBody WithdrawalDTO.ApplyForm form) {
        String message = withdrawalService.requestWithdrawal(groupId, requireMemberId(authentication), form);
        return ResponseEntity.ok(Map.of("message", message));
    }

    // 2. [정산안] 모임장의 정산 금액·근거 등록 및 동의 요청 (CONSENT_PENDING)
    @PostMapping("/{withdrawalId}/settlement")
    public ResponseEntity registerSettlement(
            @PathVariable Long groupId,
            @PathVariable Long withdrawalId,
            Authentication authentication,
            @RequestBody WithdrawalDTO.SettlementForm form) {
        withdrawalService.registerSettlementPlan(groupId, withdrawalId, requireMemberId(authentication), form);
        return ResponseEntity.ok(Map.of("message", "정산안이 등록되었으며 나머지 회원 전원에게 동의 요청 알림을 발송했습니다."));
    }

    // 3. [동의] 나머지 회원의 정산안 공식 동의 (CONSENT type=SETTLEMENT)
    @PostMapping("/{withdrawalId}/consents")
    public ResponseEntity submitConsent(
            @PathVariable Long groupId,
            @PathVariable Long withdrawalId,
            Authentication authentication,
            @RequestBody WithdrawalDTO.ConsentForm form) {
        String message = withdrawalService.submitSettlementConsent(
                groupId, withdrawalId, requireMemberId(authentication), form);
        return ResponseEntity.ok(Map.of("message", message));
    }

    // 4. [재확인] 신청자의 탈퇴 재확인 요청 (RECONFIRM_COUNT + 1)
    @PostMapping("/{withdrawalId}/reconfirm")
    public ResponseEntity reconfirmWithdrawal(
            @PathVariable Long groupId,
            @PathVariable Long withdrawalId,
            Authentication authentication) {
        withdrawalService.reconfirmWithdrawal(groupId, withdrawalId, requireMemberId(authentication));
        return ResponseEntity.ok(Map.of("message", "모임장에게 탈퇴 재확인 요청 알림을 발송했습니다."));
    }

    // 5. [승인] 모임장의 전원 동의 검증 후 최종 탈퇴 승인 (APPROVED & GYE_MEMBER.STATUS=WITHDRAWN)
    @PostMapping("/{withdrawalId}/approve")
    public ResponseEntity approveWithdrawal(
            @PathVariable Long groupId,
            @PathVariable Long withdrawalId,
            Authentication authentication) {
        withdrawalService.approveWithdrawal(groupId, withdrawalId, requireMemberId(authentication));
        return ResponseEntity.ok(Map.of("message", "중도 탈퇴가 최종 승인되었습니다."));
    }

    @GetMapping("/{withdrawalId}/consent-targets")
    public ResponseEntity<List<WithdrawalDTO.ConsentTargetResponse>> getConsentTargets(
            @PathVariable Long groupId,
            @PathVariable Long withdrawalId,
            Authentication authentication) {
        requireAuthenticated(authentication);
        if (hasAuthority(authentication, "ROLE_ADMIN")) {
            Long adminId = parseSubjectId(authentication);
            return ResponseEntity.ok(
                    withdrawalService.getSettlementConsentTargetsForAdmin(groupId, withdrawalId, adminId));
        }
        if (hasAuthority(authentication, "ROLE_MEMBER")) {
            Long memberId = parseSubjectId(authentication);
            return ResponseEntity.ok(
                    withdrawalService.getSettlementConsentTargets(groupId, withdrawalId, memberId));
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "동의 대상자 현황을 조회할 권한이 없습니다.");
    }

    @PostMapping("/{withdrawalId}/consent-targets/{memberId}/exemption")
    public ResponseEntity exemptConsentTarget(
            @PathVariable Long groupId,
            @PathVariable Long withdrawalId,
            @PathVariable Long memberId,
            Authentication authentication,
            @RequestBody WithdrawalDTO.ConsentExemptionForm form) {
        Long adminId = requireAdminId(authentication);
        withdrawalService.exemptSettlementConsentTarget(
                groupId, withdrawalId, memberId, adminId, form == null ? null : form.getReason());
        return ResponseEntity.ok(Map.of("message", "해당 회원의 정산안 동의 의무를 면제하고 감사 기록을 남겼습니다."));
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
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "플랫폼 관리자만 동의 의무를 면제할 수 있습니다.");
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