package com.moava.group.procedures.controller;

import com.moava.group.procedures.dto.DissolutionDTO;
import com.moava.group.procedures.dto.PayoutOrderDTO;
import com.moava.group.procedures.dto.WithdrawalDTO;
import com.moava.group.procedures.service.DissolutionService;
import com.moava.group.procedures.service.PayoutOrderService;
import com.moava.group.procedures.service.WithdrawalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProceduresControllerAuthTest {

    @Mock
    private PayoutOrderService payoutOrderService;

    @Mock
    private WithdrawalService withdrawalService;

    @Mock
    private DissolutionService dissolutionService;

    private static Authentication auth(String subject, String role) {
        return new UsernamePasswordAuthenticationToken(subject, null, List.of(new SimpleGrantedAuthority(role)));
    }

    @Test
    @DisplayName("PayoutOrderController: ADMIN 토큰으로 회원용 API 호출 시 403 차단")
    void payoutOrder_blocksAdminToken() {
        PayoutOrderController controller = new PayoutOrderController(payoutOrderService);
        Authentication adminAuth = auth("1", "ROLE_ADMIN");

        assertThatThrownBy(() -> controller.getPayoutRequestList(101L, adminAuth))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThatThrownBy(() -> controller.requestOrUpdateOrder(101L, adminAuth, new PayoutOrderDTO.RequestForm()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verifyNoInteractions(payoutOrderService);
    }

    @Test
    @DisplayName("PayoutOrderController: MEMBER 토큰은 정상 위임, 익명·비정상 ID는 401")
    void payoutOrder_allowsMemberAndRejectsInvalidAuth() {
        PayoutOrderController controller = new PayoutOrderController(payoutOrderService);
        when(payoutOrderService.getPayoutOrderList(101L, 10L)).thenReturn(List.of());

        assertThat(controller.getPayoutRequestList(101L, auth("10", "ROLE_MEMBER")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        verify(payoutOrderService).getPayoutOrderList(101L, 10L);

        Authentication anon = new AnonymousAuthenticationToken(
                "k", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        assertThatThrownBy(() -> controller.getPayoutRequestList(101L, anon))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThatThrownBy(() -> controller.getPayoutRequestList(101L, auth("0", "ROLE_MEMBER")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("WithdrawalController: ADMIN 토큰은 회원용 탈퇴 API 403, consent-targets는 역할별 분기, 면제는 ADMIN 전용")
    void withdrawal_enforcesRoleSeparation() {
        WithdrawalController controller = new WithdrawalController(withdrawalService);
        Authentication adminAuth = auth("2", "ROLE_ADMIN");
        Authentication memberAuth = auth("7", "ROLE_MEMBER");

        assertThatThrownBy(() -> controller.getWithdrawalList(101L, adminAuth))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThatThrownBy(() -> controller.requestWithdrawal(101L, adminAuth, new WithdrawalDTO.ApplyForm()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThatThrownBy(() -> controller.exemptConsentTarget(101L, 5L, 9L, memberAuth, new WithdrawalDTO.ConsentExemptionForm()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        when(withdrawalService.getSettlementConsentTargetsForAdmin(101L, 5L, 2L)).thenReturn(List.of());
        when(withdrawalService.getSettlementConsentTargets(101L, 5L, 7L)).thenReturn(List.of());

        assertThat(controller.getConsentTargets(101L, 5L, adminAuth).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.getConsentTargets(101L, 5L, memberAuth).getStatusCode()).isEqualTo(HttpStatus.OK);

        verify(withdrawalService).getSettlementConsentTargetsForAdmin(101L, 5L, 2L);
        verify(withdrawalService).getSettlementConsentTargets(101L, 5L, 7L);
    }

    @Test
    @DisplayName("DissolutionController: 회원용은 MEMBER 전용, 관리자용은 ADMIN 전용")
    void dissolution_enforcesRoleSeparation() {
        DissolutionController controller = new DissolutionController(dissolutionService);
        Authentication adminAuth = auth("1", "ROLE_ADMIN");
        Authentication memberAuth = auth("1", "ROLE_MEMBER");

        assertThatThrownBy(() -> controller.getGroupDissolution(101L, adminAuth))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThatThrownBy(() -> controller.requestDissolution(101L, adminAuth, new DissolutionDTO.RequestForm()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThatThrownBy(() -> controller.getAdminDissolutionList(memberAuth, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThatThrownBy(() -> controller.approveDissolution(101L, memberAuth, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verifyNoInteractions(dissolutionService);
    }
}
