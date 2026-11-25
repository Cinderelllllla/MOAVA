package com.moava.group.procedures.controller;

import com.moava.group.procedures.dto.PayoutOrderDTO;
import com.moava.group.procedures.service.PayoutOrderService;
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
@RequestMapping("/api/v1/groups/{groupId}/payout-requests")
public class PayoutOrderController {

    private final PayoutOrderService payoutOrderService;

    // Lombok(@RequiredArgsConstructor) 대신 직접 생성자 주입 (에러 원천 차단)
    public PayoutOrderController(PayoutOrderService payoutOrderService) {
        this.payoutOrderService = payoutOrderService;
    }

    // 1. 신청·동의 현황 목록 조회 (AI 추천 회차 포함)
    @GetMapping
    public ResponseEntity<List<PayoutOrderDTO.StatusResponse>> getPayoutRequestList(
            @PathVariable Long groupId,
            Authentication authentication) {
        return ResponseEntity.ok(payoutOrderService.getPayoutOrderList(groupId, loginMemberId(authentication)));
    }

    // 2. 회원의 수령 순서 신청 및 수정 (REQUESTED)
    @PostMapping
    public ResponseEntity requestOrUpdateOrder(
            @PathVariable Long groupId,
            Authentication authentication,
            @RequestBody PayoutOrderDTO.RequestForm form) {
        String message = payoutOrderService.requestOrUpdatePayoutOrder(
                groupId, loginMemberId(authentication), form);
        return ResponseEntity.ok(Map.of("message", message));
    }

    // 3. 모임장의 동의 요청 발송 (CONSENT_PENDING 전환)
    @PostMapping("/{requestId}/request-consent")
    public ResponseEntity requestConsent(
            @PathVariable Long groupId,
            @PathVariable Long requestId,
            Authentication authentication) {
        payoutOrderService.requestConsentByLeader(groupId, requestId, loginMemberId(authentication));
        return ResponseEntity.ok(Map.of("message", "신청자 제외 전원에게 동의 요청 알림을 발송했습니다."));
    }

    // 4. 모임원의 변경 순서 개별 동의 (Y/N)
    @PostMapping("/{requestId}/consents")
    public ResponseEntity submitConsent(
            @PathVariable Long groupId,
            @PathVariable Long requestId,
            Authentication authentication,
            @RequestBody PayoutOrderDTO.ConsentForm form) {
        String message = payoutOrderService.submitMemberConsent(
                groupId, requestId, loginMemberId(authentication), form);
        return ResponseEntity.ok(Map.of("message", message));
    }

    // 5. 모임장의 동의 확인 후 최종 확정 (CONFIRMED)
    @PostMapping("/{requestId}/confirm")
    public ResponseEntity confirmOrder(
            @PathVariable Long groupId,
            @PathVariable Long requestId,
            Authentication authentication) {
        payoutOrderService.confirmPayoutOrder(groupId, requestId, loginMemberId(authentication));
        return ResponseEntity.ok(Map.of("message", "수령 순서가 최종 확정되었습니다."));
    }

    private static Long loginMemberId(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        boolean isMember = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_MEMBER"::equals);
        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "일반 회원만 이용할 수 있습니다.");
        }
        return parseSubjectId(authentication);
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