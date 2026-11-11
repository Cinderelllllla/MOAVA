package com.moava.group.procedures.controller;

import com.moava.group.procedures.dto.PayoutOrderDTO;
import com.moava.group.procedures.service.PayoutOrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/payout-requests")
public class PayoutOrderController {

    private final PayoutOrderService payoutOrderService;

    // Lombok(@RequiredArgsConstructor) 대신 직접 생성자 주입 (에러 원천 차단)
    public PayoutOrderController(PayoutOrderService payoutOrderService) {
        this.payoutOrderService = payoutOrderService;
    }

    // 1. 신청·동의 현황 목록 조회 (AI 추천 회차 포함)
    @GetMapping
    public ResponseEntity<List<PayoutOrderDTO.StatusResponse>> getPayoutRequestList(@PathVariable Long groupId) {
        return ResponseEntity.ok(payoutOrderService.getPayoutOrderList(groupId));
    }

    // 2. 회원의 수령 순서 신청 및 수정 (REQUESTED)
    @PostMapping
    public ResponseEntity requestOrUpdateOrder(
            @PathVariable Long groupId,
            @RequestBody PayoutOrderDTO.RequestForm form) {
        String message = payoutOrderService.requestOrUpdatePayoutOrder(groupId, form);
        return ResponseEntity.ok(Map.of("message", message));
    }

    // 3. 모임장의 동의 요청 발송 (CONSENT_PENDING 전환)
    @PostMapping("/{requestId}/request-consent")
    public ResponseEntity requestConsent(
            @PathVariable Long groupId,
            @PathVariable Long requestId,
            @RequestBody PayoutOrderDTO.LeaderActionForm form) {
        payoutOrderService.requestConsentByLeader(groupId, requestId, form.getLeaderId());
        return ResponseEntity.ok(Map.of("message", "신청자 제외 전원에게 동의 요청 알림을 발송했습니다."));
    }

    // 4. 모임원의 변경 순서 개별 동의 (Y/N)
    @PostMapping("/{requestId}/consents")
    public ResponseEntity submitConsent(
            @PathVariable Long groupId,
            @PathVariable Long requestId,
            @RequestBody PayoutOrderDTO.ConsentForm form) {
        String message = payoutOrderService.submitMemberConsent(groupId, requestId, form);
        return ResponseEntity.ok(Map.of("message", message));
    }

    // 5. 모임장의 동의 확인 후 최종 확정 (CONFIRMED)
    @PostMapping("/{requestId}/confirm")
    public ResponseEntity confirmOrder(
            @PathVariable Long groupId,
            @PathVariable Long requestId,
            @RequestBody PayoutOrderDTO.LeaderActionForm form) {
        payoutOrderService.confirmPayoutOrder(groupId, requestId, form.getLeaderId());
        return ResponseEntity.ok(Map.of("message", "수령 순서가 최종 확정되었습니다."));
    }
}