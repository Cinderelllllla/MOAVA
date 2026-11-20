package com.moava.group.procedures.service;

import com.moava.common.response.ApiResponse;
import com.moava.group.procedures.controller.DissolutionController;
import com.moava.group.procedures.controller.ProcedureExceptionHandler;
import com.moava.group.procedures.dto.DissolutionDTO;
import com.moava.group.procedures.dto.PayoutOrderDTO;
import com.moava.group.procedures.dto.WithdrawalDTO;
import com.moava.group.procedures.exception.ProcedureException;
import com.moava.group.procedures.mapper.DissolutionMapper;
import com.moava.group.procedures.mapper.PayoutOrderMapper;
import com.moava.group.procedures.mapper.WithdrawalMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProceduresWorkflowTest {
    private static final Long GROUP_ID = 1L;
    private static final Long OWNER_ID = 10L;
    private static final Long MEMBER_ID = 20L;
    private static final Long ADMIN_ID = 7L;
    private static final Long REQUEST_ID = 100L;

    @Nested
    class DissolutionWorkflow {
        @Mock
        private DissolutionMapper mapper;

        @InjectMocks
        private DissolutionService service;

        @Test
        void statusIsAvailableOnlyToActiveGroupMembers() {
            when(mapper.selectGroupInfo(GROUP_ID)).thenReturn(Map.of("GROUP_ID", GROUP_ID));
            when(mapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(0);

            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> service.getGroupDissolutionStatus(GROUP_ID, MEMBER_ID));

            assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
            verify(mapper, never()).selectLatestDissolutionByGroup(GROUP_ID);
        }

        @Test
        void activeMemberCanReadDissolutionStatusAndEstimate() {
            DissolutionDTO.StatusResponse response = new DissolutionDTO.StatusResponse();
            response.setMemberCount(2);
            response.setTotalConfirmedAmount(1_001L);
            when(mapper.selectGroupInfo(GROUP_ID)).thenReturn(Map.of("GROUP_ID", GROUP_ID));
            when(mapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);
            when(mapper.selectLatestDissolutionByGroup(GROUP_ID)).thenReturn(response);

            DissolutionDTO.StatusResponse result = service.getGroupDissolutionStatus(GROUP_ID, MEMBER_ID);

            assertEquals(500L, result.getPerMemberRefundAmount());
        }

        @Test
        void zeroActiveMembersProducesZeroRefundEstimateAndRejectedHasNoApprovedAt() {
            DissolutionDTO.StatusResponse response = new DissolutionDTO.StatusResponse();
            response.setStatus("REJECTED");
            response.setDecidedAt(new Date());
            response.setMemberCount(0);
            response.setTotalConfirmedAmount(100_000L);
            when(mapper.selectGroupInfo(GROUP_ID)).thenReturn(Map.of("GROUP_ID", GROUP_ID));
            when(mapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);
            when(mapper.selectLatestDissolutionByGroup(GROUP_ID)).thenReturn(response);

            DissolutionDTO.StatusResponse result = service.getGroupDissolutionStatus(GROUP_ID, MEMBER_ID);

            assertEquals(0L, result.getPerMemberRefundAmount());
            assertEquals(null, result.getApprovedAt());
        }

        @Test
        void requestCompletesWhenGroupHasNoOpenProcedures() {
            when(mapper.lockGroupForDissolution(GROUP_ID))
                    .thenReturn(groupInfo(OWNER_ID, "ACTIVE", "테스트 모임"));
            when(mapper.selectPendingDissolutionByGroup(GROUP_ID)).thenReturn(null);
            when(mapper.countActiveWithdrawals(GROUP_ID)).thenReturn(0);
            when(mapper.countActivePayoutRequests(GROUP_ID)).thenReturn(0);
            when(mapper.selectActiveAdminIds()).thenReturn(List.of(ADMIN_ID));
            when(mapper.updateGroupStatus(GROUP_ID, "ACTIVE", "DISSOLVE_PENDING")).thenReturn(1);

            DissolutionDTO.RequestForm form = new DissolutionDTO.RequestForm();
            form.setReason("운영 종료");
            service.requestDissolution(GROUP_ID, OWNER_ID, form);

            verify(mapper).insertDissolutionRequest(GROUP_ID, OWNER_ID, "운영 종료");
            verify(mapper).insertAdminNotification(
                    ADMIN_ID, "DISSOLVE_REQUEST", "해산 승인 검토 요청",
                    "[테스트 모임] 해산 신청이 접수되었습니다. 관리자 화면에서 사유를 확인해 주세요.",
                    "/admin/dissolve", GROUP_ID);
            verify(mapper).insertMemberAuditLog(GROUP_ID, OWNER_ID, "DISSOLVE_REQUEST",
                    "계모임 해산 신청 접수 (사유: 운영 종료)");
        }
    }

    @Nested
    class PayoutOrderWorkflow {
        @Mock
        private PayoutOrderMapper mapper;

        @InjectMocks
        private PayoutOrderService service;

        @Test
        void rejectsRoundOutsideConfiguredGroupSizeAsBadRequest() {
            when(mapper.lockGroupForOrderUpdate(GROUP_ID)).thenReturn(GROUP_ID);
            when(mapper.selectGroupInfo(GROUP_ID)).thenReturn(groupInfo(OWNER_ID, "ACTIVE", 3));
            when(mapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);
            PayoutOrderDTO.RequestForm form = new PayoutOrderDTO.RequestForm();
            form.setRoundNo(4);
            form.setReason("요청 사유");

            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> service.requestOrUpdatePayoutOrder(GROUP_ID, MEMBER_ID, form));

            assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
            verify(mapper, never()).insertPayoutRequest(any(), any(), any(), any(), any());
        }

        @Test
        void rejectsOverlongReasonAsBadRequest() {
            when(mapper.lockGroupForOrderUpdate(GROUP_ID)).thenReturn(GROUP_ID);
            when(mapper.selectGroupInfo(GROUP_ID)).thenReturn(groupInfo(OWNER_ID, "ACTIVE", 3));
            when(mapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);
            PayoutOrderDTO.RequestForm form = new PayoutOrderDTO.RequestForm();
            form.setRoundNo(1);
            form.setReason("가".repeat(167)); // 501 bytes in UTF-8

            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> service.requestOrUpdatePayoutOrder(GROUP_ID, MEMBER_ID, form));

            assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
            verify(mapper, never()).insertPayoutRequest(any(), any(), any(), any(), any());
        }

        @Test
        void confirmationConflictsUntilEveryActiveMemberHasAgreed() {
            when(mapper.lockGroupForOrderUpdate(GROUP_ID)).thenReturn(GROUP_ID);
            when(mapper.selectGroupInfo(GROUP_ID)).thenReturn(groupInfo(OWNER_ID, "ACTIVE", 3));
            when(mapper.selectPayoutRequestForUpdate(GROUP_ID, REQUEST_ID))
                    .thenReturn(payoutRequest("CONSENT_PENDING"));
            when(mapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);
            when(mapper.countConfirmedRoundDuplicate(GROUP_ID, 2, REQUEST_ID)).thenReturn(0);
            when(mapper.countConfirmedRequestByMember(GROUP_ID, MEMBER_ID)).thenReturn(0);
            when(mapper.lockActiveConsentTargets(GROUP_ID, REQUEST_ID, MEMBER_ID)).thenReturn(List.of(30L));
            when(mapper.countAgreedConsents(GROUP_ID, REQUEST_ID, MEMBER_ID)).thenReturn(0);

            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> service.confirmPayoutOrder(GROUP_ID, REQUEST_ID, OWNER_ID));

            assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
            verify(mapper, never()).updatePayoutRequestStatus(
                    GROUP_ID, REQUEST_ID, "CONSENT_PENDING", "CONFIRMED");
        }

        @Test
        void confirmationRejectsInactiveApplicantAndEmptyConsentSnapshot() {
            when(mapper.lockGroupForOrderUpdate(GROUP_ID)).thenReturn(GROUP_ID);
            when(mapper.selectGroupInfo(GROUP_ID)).thenReturn(groupInfo(OWNER_ID, "ACTIVE", 3));
            when(mapper.selectPayoutRequestForUpdate(GROUP_ID, REQUEST_ID))
                    .thenReturn(payoutRequest("CONSENT_PENDING"));
            when(mapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(0);

            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> service.confirmPayoutOrder(GROUP_ID, REQUEST_ID, OWNER_ID));

            assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
            verify(mapper, never()).updatePayoutRequestStatus(any(), any(), any(), any());
        }

        @Test
        void submitConsentReturnsNotFoundWhenRequestDoesNotExist() {
            when(mapper.lockGroupForOrderUpdate(GROUP_ID)).thenReturn(GROUP_ID);
            when(mapper.selectGroupInfo(GROUP_ID)).thenReturn(groupInfo(OWNER_ID, "ACTIVE", 3));
            when(mapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);
            when(mapper.selectPayoutRequestForUpdate(GROUP_ID, REQUEST_ID)).thenReturn(null);

            PayoutOrderDTO.ConsentForm form = new PayoutOrderDTO.ConsentForm();
            form.setAgreedYn("Y");

            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> service.submitMemberConsent(GROUP_ID, REQUEST_ID, MEMBER_ID, form));

            assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        }
    }

    @Nested
    class WithdrawalWorkflow {
        @Mock
        private WithdrawalMapper mapper;

        @InjectMocks
        private WithdrawalService service;

        @Test
        void duplicateActiveRequestIsAConflict() {
            when(mapper.selectGroupInfoForUpdate(GROUP_ID)).thenReturn(groupInfo(OWNER_ID, "ACTIVE"));
            when(mapper.selectGyeMemberInfo(GROUP_ID, MEMBER_ID))
                    .thenReturn(Map.of("STATUS", "ACTIVE", "PAYOUT_RECEIVED_YN", "N"));
            when(mapper.countActiveWithdrawalByMember(GROUP_ID, MEMBER_ID)).thenReturn(1);

            ProcedureException error = assertThrows(ProcedureException.class,
                    () -> service.requestWithdrawal(GROUP_ID, MEMBER_ID, validApplyForm()));

            assertEquals(HttpStatus.CONFLICT, error.getStatus());
            verify(mapper, never()).insertWithdrawalRequest(any(), any(), anyString(), anyString(), anyString());
        }

        @Test
        void settlementCannotBeApprovedBeforeAllSnapshotTargetsResolve() {
            when(mapper.selectGroupInfoForUpdate(GROUP_ID)).thenReturn(groupInfo(OWNER_ID, "ACTIVE"));
            when(mapper.selectWithdrawalByIdForUpdate(GROUP_ID, REQUEST_ID))
                    .thenReturn(consentPendingRequest());
            when(mapper.selectGyeMemberInfoForUpdate(GROUP_ID, MEMBER_ID))
                    .thenReturn(Map.of("STATUS", "ACTIVE"));
            when(mapper.countSettlementConsentTargets(REQUEST_ID)).thenReturn(2);
            when(mapper.countUnresolvedActiveSettlementConsentTargets(GROUP_ID, REQUEST_ID)).thenReturn(1);

            ProcedureException error = assertThrows(ProcedureException.class,
                    () -> service.approveWithdrawal(GROUP_ID, REQUEST_ID, OWNER_ID));

            assertEquals(HttpStatus.CONFLICT, error.getStatus());
            verify(mapper, never()).updateWithdrawalStatus(
                    GROUP_ID, REQUEST_ID, "CONSENT_PENDING", "APPROVED");
        }

        @Test
        void reconfirmIsAllowedDuringConsentAndIncrementsCounter() {
            when(mapper.selectGroupInfoForUpdate(GROUP_ID)).thenReturn(groupInfo(OWNER_ID, "ACTIVE"));
            when(mapper.selectWithdrawalByIdForUpdate(GROUP_ID, REQUEST_ID))
                    .thenReturn(consentPendingRequest());
            when(mapper.selectGyeMemberInfo(GROUP_ID, MEMBER_ID))
                    .thenReturn(Map.of("STATUS", "ACTIVE"));

            service.reconfirmWithdrawal(GROUP_ID, REQUEST_ID, MEMBER_ID);

            verify(mapper).incrementReconfirmCount(REQUEST_ID);
            verify(mapper).insertNotification(
                    OWNER_ID, "CONSENT_REQUEST", "중도 탈퇴 재확인 요청",
                    "탈퇴 신청 회원이 정산 및 탈퇴 처리 재확인을 요청했습니다.",
                    "/operations?groupId=" + GROUP_ID, REQUEST_ID);
        }

        @Test
        void reconfirmRejectsWhenCountReachesColumnLimit() {
            Map<String, Object> req = consentPendingRequest();
            req.put("RECONFIRM_COUNT", 99);
            when(mapper.selectGroupInfoForUpdate(GROUP_ID)).thenReturn(groupInfo(OWNER_ID, "ACTIVE"));
            when(mapper.selectWithdrawalByIdForUpdate(GROUP_ID, REQUEST_ID)).thenReturn(req);
            when(mapper.selectGyeMemberInfo(GROUP_ID, MEMBER_ID))
                    .thenReturn(Map.of("STATUS", "ACTIVE"));

            ProcedureException error = assertThrows(ProcedureException.class,
                    () -> service.reconfirmWithdrawal(GROUP_ID, REQUEST_ID, MEMBER_ID));

            assertEquals(HttpStatus.CONFLICT, error.getStatus());
            verify(mapper, never()).incrementReconfirmCount(REQUEST_ID);
        }
    }

    @Test
    void dissolutionStatusEndpointUsesAuthenticatedMemberId() {
        DissolutionService dissolutionService = org.mockito.Mockito.mock(DissolutionService.class);
        DissolutionController controller = new DissolutionController(dissolutionService);
        var authentication = new UsernamePasswordAuthenticationToken(
                String.valueOf(MEMBER_ID), null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
        when(dissolutionService.getGroupDissolutionStatus(GROUP_ID, MEMBER_ID)).thenReturn(null);

        controller.getGroupDissolution(GROUP_ID, authentication);

        verify(dissolutionService).getGroupDissolutionStatus(GROUP_ID, MEMBER_ID);
    }

    @Test
    void procedureErrorsKeepTheirHttpStatusAndStructuredResponse() {
        ProcedureExceptionHandler handler = new ProcedureExceptionHandler();

        var response = handler.handleProcedureException(ProcedureException.conflict("상태 충돌"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        ApiResponse.Error body = response.getBody();
        assertEquals("CONFLICT", body.error().get("code"));
        assertEquals("상태 충돌", body.error().get("message"));
        assertEquals(12, body.requestId().length());
    }

    private static Map<String, Object> groupInfo(Long ownerId, String status, String title) {
        return Map.of("OWNER_ID", ownerId, "STATUS", status, "TITLE", title);
    }

    private static Map<String, Object> groupInfo(Long ownerId, String status, int maxMembers) {
        return Map.of("OWNER_ID", ownerId, "STATUS", status, "MAX_MEMBERS", maxMembers);
    }

    private static Map<String, Object> groupInfo(Long ownerId, String status) {
        return Map.of("OWNER_ID", ownerId, "STATUS", status);
    }

    private static Map<String, Object> payoutRequest(String status) {
        return Map.of("MEMBER_ID", MEMBER_ID, "ROUND_NO", 2, "STATUS", status);
    }

    private static WithdrawalDTO.ApplyForm validApplyForm() {
        WithdrawalDTO.ApplyForm form = new WithdrawalDTO.ApplyForm();
        form.setDetail("개인 사정");
        form.setReasonType("PERSONAL");
        return form;
    }

    private static Map<String, Object> consentPendingRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("GROUP_ID", GROUP_ID);
        request.put("MEMBER_ID", MEMBER_ID);
        request.put("STATUS", "CONSENT_PENDING");
        request.put("SETTLEMENT_AMOUNT", 10_000L);
        request.put("SETTLEMENT_AT", new Date());
        return request;
    }
}
