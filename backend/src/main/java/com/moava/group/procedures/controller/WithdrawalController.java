package com.moava.group.procedures.controller;

import com.moava.group.procedures.dto.WithdrawalDTO;
import com.moava.group.procedures.service.WithdrawalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<List<WithdrawalDTO.StatusResponse>> getWithdrawalList(@PathVariable Long groupId) {
        return ResponseEntity.ok(withdrawalService.getWithdrawalList(groupId));
    }

    // 1. [신청] 일반 회원의 중도 탈퇴 신청 (SETTLEMENT_PENDING)
    @PostMapping
    public ResponseEntity requestWithdrawal(
            @PathVariable Long groupId,
            @RequestBody WithdrawalDTO.ApplyForm form) {
        String message = withdrawalService.requestWithdrawal(groupId, form);
        return ResponseEntity.ok(Map.of("message", message));
    }

    // 2. [정산안] 모임장의 정산 금액·근거 등록 및 동의 요청 (CONSENT_PENDING)
    @PostMapping("/{withdrawalId}/settlement")
    public ResponseEntity registerSettlement(
            @PathVariable Long groupId,
            @PathVariable Long withdrawalId,
            @RequestBody WithdrawalDTO.SettlementForm form) {
        withdrawalService.registerSettlementPlan(groupId, withdrawalId, form);
        return ResponseEntity.ok(Map.of("message", "정산안이 등록되었으며 나머지 회원 전원에게 동의 요청 알림을 발송했습니다."));
    }

    // 3. [동의] 나머지 회원의 정산안 공식 동의 (CONSENT type=SETTLEMENT)
    @PostMapping("/{withdrawalId}/consents")
    public ResponseEntity submitConsent(
            @PathVariable Long groupId,
            @PathVariable Long withdrawalId,
            @RequestBody WithdrawalDTO.ConsentForm form) {
        String message = withdrawalService.submitSettlementConsent(groupId, withdrawalId, form);
        return ResponseEntity.ok(Map.of("message", message));
    }

    // 4. [재확인] 신청자의 탈퇴 재확인 요청 (RECONFIRM_COUNT + 1)
    @PostMapping("/{withdrawalId}/reconfirm")
    public ResponseEntity reconfirmWithdrawal(
            @PathVariable Long groupId,
            @PathVariable Long withdrawalId,
            @RequestBody WithdrawalDTO.ActionForm form) {
        withdrawalService.reconfirmWithdrawal(groupId, withdrawalId, form.getActorId());
        return ResponseEntity.ok(Map.of("message", "모임장에게 탈퇴 재확인 요청 알림을 발송했습니다."));
    }

    // 5. [승인] 모임장의 전원 동의 검증 후 최종 탈퇴 승인 (APPROVED & GYE_MEMBER.STATUS=WITHDRAWN)
    @PostMapping("/{withdrawalId}/approve")
    public ResponseEntity approveWithdrawal(
            @PathVariable Long groupId,
            @PathVariable Long withdrawalId,
            @RequestBody WithdrawalDTO.ActionForm form) {
        withdrawalService.approveWithdrawal(groupId, withdrawalId, form.getActorId());
        return ResponseEntity.ok(Map.of("message", "중도 탈퇴가 최종 승인되었습니다."));
    }
}