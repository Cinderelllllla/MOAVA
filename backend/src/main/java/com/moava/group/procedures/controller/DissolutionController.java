package com.moava.group.procedures.controller;

import com.moava.group.procedures.dto.DissolutionDTO;
import com.moava.group.procedures.service.DissolutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class DissolutionController {

    private final DissolutionService dissolutionService;

    public DissolutionController(DissolutionService dissolutionService) {
        this.dissolutionService = dissolutionService;
    }

    // [S10 ⑤] 특정 모임의 해산 상태 배지 및 상세 조회
    @GetMapping("/api/v1/groups/{groupId}/dissolution")
    public ResponseEntity<DissolutionDTO.StatusResponse> getGroupDissolution(@PathVariable Long groupId) {
        return ResponseEntity.ok(dissolutionService.getGroupDissolutionStatus(groupId));
    }

    // [S10 ⑤ - 신청] 모임장의 계모임 해산 신청
    @PostMapping("/api/v1/groups/{groupId}/dissolution")
    public ResponseEntity requestDissolution(
            @PathVariable Long groupId,
            @RequestBody DissolutionDTO.RequestForm form) {
        String message = dissolutionService.requestDissolution(groupId, form);
        return ResponseEntity.ok(Map.of("message", message));
    }

    // [A04] 관리자용 해산 신청 목록 및 종료된 모임 기록 조회 (status=PENDING 또는 APPROVED, 미전달 시 전체)
    @GetMapping("/api/v1/admin/dissolutions")
    public ResponseEntity<List<DissolutionDTO.StatusResponse>> getAdminDissolutionList(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(dissolutionService.getAdminDissolutionList(status));
    }

    // [A04 - 승인] ADMIN의 미종결 증빙 검증 후 해산 최종 승인 (admin.approveDissolve(groupId) 매핑)
    @PostMapping("/api/v1/admin/groups/{groupId}/dissolution/approve")
    public ResponseEntity approveDissolution(
            @PathVariable Long groupId,
            @RequestBody(required = false) DissolutionDTO.AdminDecisionForm form) {
        Long adminId = (form != null) ? form.getEffectiveAdminId() : 1L;
        String message = dissolutionService.approveDissolutionByAdmin(groupId, adminId);
        return ResponseEntity.ok(Map.of("message", message));
    }

    // [A04 - 반려] ADMIN의 해산 신청 반려 (admin.rejectDissolve(groupId, reason) 매핑)
    @PostMapping("/api/v1/admin/groups/{groupId}/dissolution/reject")
    public ResponseEntity rejectDissolution(
            @PathVariable Long groupId,
            @RequestBody(required = false) DissolutionDTO.AdminDecisionForm form) {
        Long adminId = (form != null) ? form.getEffectiveAdminId() : 1L;
        String reason = (form != null) ? form.getRejectReason() : null;
        String message = dissolutionService.rejectDissolutionByAdmin(groupId, adminId, reason);
        return ResponseEntity.ok(Map.of("message", message));
    }
}