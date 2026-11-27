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
import static org.mockito.ArgumentMatchers.eq;
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
        void noDissolutionRequestReturnsStructuredNoneStatusWithNotice() {
            when(mapper.selectGroupInfo(GROUP_ID))
                    .thenReturn(groupInfo(OWNER_ID, "ACTIVE", "테스트 모임"));
            when(mapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);
            when(mapper.selectLatestDissolutionByGroup(GROUP_ID)).thenReturn(null);

            DissolutionDTO.StatusResponse result = service.getGroupDissolutionStatus(GROUP_ID, MEMBER_ID);

            assertEquals("NONE", result.getStatus());
            assertEquals("운영 중", result.getStatusBadge());
            assertEquals(GROUP_ID, result.getGroupId());
            assertEquals("테스트 모임", result.getGroupTitle());
            assertEquals("ACTIVE", result.getGroupStatus());
            assertEquals(0L, result.getPerMemberRefundAmount());
            org.junit.jupiter.api.Assertions.assertNotNull(result.getSettlementNotice());
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
        void requestRejectsRecruitingAndDissolvePendingAndAllowsActiveWithConfirmedProof() {
            when(mapper.lockGroupForOrderUpdate(GROUP_ID)).thenReturn(GROUP_ID);
            PayoutOrderDTO.RequestForm form = new PayoutOrderDTO.RequestForm();
            form.setRoundNo(1);
            form.setReason("운영 개시 및 납입 완료 후 신청");

            // RECRUITING 차단
            when(mapper.selectGroupInfo(GROUP_ID)).thenReturn(groupInfo(OWNER_ID, "RECRUITING", 3));
            ResponseStatusException recruitingError = assertThrows(ResponseStatusException.class,
                    () -> service.requestOrUpdatePayoutOrder(GROUP_ID, MEMBER_ID, form));
            assertEquals(HttpStatus.CONFLICT, recruitingError.getStatusCode());

            // DISSOLVE_PENDING 차단
            when(mapper.selectGroupInfo(GROUP_ID)).thenReturn(groupInfo(OWNER_ID, "DISSOLVE_PENDING", 3));
            ResponseStatusException dissolveError = assertThrows(ResponseStatusException.class,
                    () -> service.requestOrUpdatePayoutOrder(GROUP_ID, MEMBER_ID, form));
            assertEquals(HttpStatus.CONFLICT, dissolveError.getStatusCode());

            // ACTIVE + 납입 증빙 승인 완료 -> 허용
            when(mapper.selectGroupInfo(GROUP_ID)).thenReturn(groupInfo(OWNER_ID, "ACTIVE", 3));
            when(mapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);
            when(mapper.countConfirmedPaymentProof(GROUP_ID, MEMBER_ID)).thenReturn(1);
            when(mapper.countConfirmedRequestByMember(GROUP_ID, MEMBER_ID)).thenReturn(0);
            when(mapper.selectActiveRequestByMember(GROUP_ID, MEMBER_ID)).thenReturn(null);
            when(mapper.countConfirmedRoundDuplicate(GROUP_ID, 1, -1L)).thenReturn(0);
            when(mapper.selectAiSuggestedRound(GROUP_ID, MEMBER_ID)).thenReturn(2);

            String message = service.requestOrUpdatePayoutOrder(GROUP_ID, MEMBER_ID, form);

            assertEquals("수령 순서 신청이 완료되었습니다.", message);
            verify(mapper).insertPayoutRequest(GROUP_ID, MEMBER_ID, 1, "운영 개시 및 납입 완료 후 신청", 2);
        }

        @Test
        void requestRejectsMemberWithoutConfirmedPaymentProof() {
            when(mapper.lockGroupForOrderUpdate(GROUP_ID)).thenReturn(GROUP_ID);
            when(mapper.selectGroupInfo(GROUP_ID)).thenReturn(groupInfo(OWNER_ID, "ACTIVE", 3));
            when(mapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);
            when(mapper.countConfirmedPaymentProof(GROUP_ID, MEMBER_ID)).thenReturn(0);

            PayoutOrderDTO.RequestForm form = new PayoutOrderDTO.RequestForm();
            form.setRoundNo(1);
            form.setReason("납입 전 수령순서 신청");

            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> service.requestOrUpdatePayoutOrder(GROUP_ID, MEMBER_ID, form));

            assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
            verify(mapper, never()).insertPayoutRequest(any(), any(), any(), any(), any());
        }

        @Test
        void requestCapsRoundByActiveMembersWhenClosedBelowMaxMembers() {
            when(mapper.lockGroupForOrderUpdate(GROUP_ID)).thenReturn(GROUP_ID);
            when(mapper.selectGroupInfo(GROUP_ID)).thenReturn(
                    Map.of("OWNER_ID", OWNER_ID, "STATUS", "ACTIVE", "MAX_MEMBERS", 6, "ACTIVE_MEMBERS", 3));
            when(mapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);
            when(mapper.countConfirmedPaymentProof(GROUP_ID, MEMBER_ID)).thenReturn(1);

            PayoutOrderDTO.RequestForm form = new PayoutOrderDTO.RequestForm();
            form.setRoundNo(4);
            form.setReason("3명 마감 모임에 4회차 신청");

            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> service.requestOrUpdatePayoutOrder(GROUP_ID, MEMBER_ID, form));

            assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
            verify(mapper, never()).insertPayoutRequest(any(), any(), any(), any(), any());
        }

        @Test
        void requestRejectsAlreadyConfirmedRound() {
            when(mapper.lockGroupForOrderUpdate(GROUP_ID)).thenReturn(GROUP_ID);
            when(mapper.selectGroupInfo(GROUP_ID)).thenReturn(groupInfo(OWNER_ID, "ACTIVE", 3));
            when(mapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);
            when(mapper.countConfirmedPaymentProof(GROUP_ID, MEMBER_ID)).thenReturn(1);
            when(mapper.countConfirmedRequestByMember(GROUP_ID, MEMBER_ID)).thenReturn(0);
            when(mapper.selectActiveRequestByMember(GROUP_ID, MEMBER_ID)).thenReturn(null);
            when(mapper.countConfirmedRoundDuplicate(GROUP_ID, 2, -1L)).thenReturn(1);

            PayoutOrderDTO.RequestForm form = new PayoutOrderDTO.RequestForm();
            form.setRoundNo(2);
            form.setReason("이미 확정된 회차 신청");

            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> service.requestOrUpdatePayoutOrder(GROUP_ID, MEMBER_ID, form));

            assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
            verify(mapper, never()).insertPayoutRequest(any(), any(), any(), any(), any());
        }

        @Test
        void requestConsentRejectsAlreadyConfirmedRound() {
            when(mapper.lockGroupForOrderUpdate(GROUP_ID)).thenReturn(GROUP_ID);
            when(mapper.selectGroupInfo(GROUP_ID)).thenReturn(groupInfo(OWNER_ID, "ACTIVE", 3));
            when(mapper.selectPayoutRequestById(GROUP_ID, REQUEST_ID)).thenReturn(payoutRequest("REQUESTED"));
            when(mapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);
            when(mapper.countConfirmedRoundDuplicate(GROUP_ID, 2, REQUEST_ID)).thenReturn(1);

            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> service.requestConsentByLeader(GROUP_ID, REQUEST_ID, OWNER_ID));

            assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
            verify(mapper, never()).updatePayoutRequestStatus(any(), any(), any(), any());
        }

        @Test
        void requestConsentSendsConsentReqNotificationWithProceduresLink() {
            when(mapper.lockGroupForOrderUpdate(GROUP_ID)).thenReturn(GROUP_ID);
            when(mapper.selectGroupInfo(GROUP_ID)).thenReturn(groupInfo(OWNER_ID, "ACTIVE", 3));
            when(mapper.selectPayoutRequestById(GROUP_ID, REQUEST_ID)).thenReturn(payoutRequest("REQUESTED"));
            when(mapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);
            when(mapper.countConfirmedRoundDuplicate(GROUP_ID, 2, REQUEST_ID)).thenReturn(0);
            when(mapper.updatePayoutRequestStatus(GROUP_ID, REQUEST_ID, "REQUESTED", "CONSENT_PENDING"))
                    .thenReturn(1);
            when(mapper.selectOtherActiveMemberIds(GROUP_ID, MEMBER_ID)).thenReturn(List.of(30L));

            service.requestConsentByLeader(GROUP_ID, REQUEST_ID, OWNER_ID);

            verify(mapper).insertInitialConsent(GROUP_ID, REQUEST_ID, 30L);
            verify(mapper).insertNotification(
                    30L, "CONSENT_REQ", "수령 순서 변경 동의 요청",
                    "2회차 수령 순서 신청에 대한 동의를 진행해 주세요.",
                    "/procedures?groupId=" + GROUP_ID, REQUEST_ID);
        }

        @Test
        void rejectsRoundOutsideConfiguredGroupSizeAsBadRequest() {
            when(mapper.lockGroupForOrderUpdate(GROUP_ID)).thenReturn(GROUP_ID);
            when(mapper.selectGroupInfo(GROUP_ID)).thenReturn(groupInfo(OWNER_ID, "ACTIVE", 3));
            when(mapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);
            when(mapper.countConfirmedPaymentProof(GROUP_ID, MEMBER_ID)).thenReturn(1);
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
        void confirmationRejectsApplicantWithoutConfirmedPaymentProof() {
            when(mapper.lockGroupForOrderUpdate(GROUP_ID)).thenReturn(GROUP_ID);
            when(mapper.selectGroupInfo(GROUP_ID)).thenReturn(groupInfo(OWNER_ID, "ACTIVE", 3));
            when(mapper.selectPayoutRequestForUpdate(GROUP_ID, REQUEST_ID))
                    .thenReturn(payoutRequest("CONSENT_PENDING"));
            when(mapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);
            when(mapper.countConfirmedPaymentProof(GROUP_ID, MEMBER_ID)).thenReturn(0);

            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> service.confirmPayoutOrder(GROUP_ID, REQUEST_ID, OWNER_ID));

            assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
            verify(mapper, never()).updatePayoutRequestStatus(any(), any(), any(), any());
        }

        @Test
        void confirmationConflictsUntilEveryActiveMemberHasAgreed() {
            when(mapper.lockGroupForOrderUpdate(GROUP_ID)).thenReturn(GROUP_ID);
            when(mapper.selectGroupInfo(GROUP_ID)).thenReturn(groupInfo(OWNER_ID, "ACTIVE", 3));
            when(mapper.selectPayoutRequestForUpdate(GROUP_ID, REQUEST_ID))
                    .thenReturn(payoutRequest("CONSENT_PENDING"));
            when(mapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);
            when(mapper.countConfirmedPaymentProof(GROUP_ID, MEMBER_ID)).thenReturn(1);
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
        void confirmationAutoRejectsCompetingOpenRequestsAndNotifiesApplicants() {
            when(mapper.lockGroupForOrderUpdate(GROUP_ID)).thenReturn(GROUP_ID);
            when(mapper.selectGroupInfo(GROUP_ID)).thenReturn(groupInfo(OWNER_ID, "ACTIVE", 3));
            when(mapper.selectPayoutRequestForUpdate(GROUP_ID, REQUEST_ID))
                    .thenReturn(payoutRequest("CONSENT_PENDING"));
            when(mapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);
            when(mapper.countConfirmedPaymentProof(GROUP_ID, MEMBER_ID)).thenReturn(1);
            when(mapper.countConfirmedRoundDuplicate(GROUP_ID, 2, REQUEST_ID)).thenReturn(0);
            when(mapper.countConfirmedRequestByMember(GROUP_ID, MEMBER_ID)).thenReturn(0);
            when(mapper.lockActiveConsentTargets(GROUP_ID, REQUEST_ID, MEMBER_ID)).thenReturn(List.of(30L));
            when(mapper.countAgreedConsents(GROUP_ID, REQUEST_ID, MEMBER_ID)).thenReturn(1);
            when(mapper.updatePayoutRequestStatus(GROUP_ID, REQUEST_ID, "CONSENT_PENDING", "CONFIRMED"))
                    .thenReturn(1);
            when(mapper.selectCompetingOpenRequests(GROUP_ID, 2, REQUEST_ID))
                    .thenReturn(List.of(Map.of("PAYOUT_REQUEST_ID", 101L, "MEMBER_ID", 30L)));
            when(mapper.selectOtherActiveMemberIds(GROUP_ID, -1L)).thenReturn(List.of(MEMBER_ID, 30L));
            when(mapper.countUnconfirmedActiveMembers(GROUP_ID)).thenReturn(1);

            service.confirmPayoutOrder(GROUP_ID, REQUEST_ID, OWNER_ID);

            verify(mapper).rejectCompetingOpenRequests(GROUP_ID, 2, REQUEST_ID);
            verify(mapper).insertNotification(
                    30L, "CONSENT_RESULT", "수령 순서 신청 반려(회차 확정)",
                    "2회차가 다른 회원으로 확정되어 신청이 반려되었습니다. 다른 회차로 다시 신청해 주세요.",
                    "/procedures?groupId=" + GROUP_ID, 101L);
            verify(mapper).insertNotification(
                    MEMBER_ID, "CONSENT_RESULT", "수령 순서 확정 안내",
                    "2회차 수령 순서가 최종 확정되었습니다.",
                    "/procedures?groupId=" + GROUP_ID, REQUEST_ID);
            verify(mapper, never()).updateGroupOrderConfirmedYn(GROUP_ID, "Y");
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

        @Test
        void submitConsentReturnsForbiddenForNonTargetAndConflictForDuplicateResponse() {
            when(mapper.lockGroupForOrderUpdate(GROUP_ID)).thenReturn(GROUP_ID);
            when(mapper.selectGroupInfo(GROUP_ID)).thenReturn(groupInfo(OWNER_ID, "ACTIVE", 3));
            when(mapper.countActiveMember(GROUP_ID, 30L)).thenReturn(1);
            when(mapper.selectPayoutRequestForUpdate(GROUP_ID, REQUEST_ID))
                    .thenReturn(payoutRequest("CONSENT_PENDING"));
            PayoutOrderDTO.ConsentForm form = new PayoutOrderDTO.ConsentForm();
            form.setAgreedYn("Y");

            when(mapper.selectMemberConsentStatus(GROUP_ID, REQUEST_ID, 30L)).thenReturn(null);
            ResponseStatusException forbidden = assertThrows(ResponseStatusException.class,
                    () -> service.submitMemberConsent(GROUP_ID, REQUEST_ID, 30L, form));
            assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());

            when(mapper.selectMemberConsentStatus(GROUP_ID, REQUEST_ID, 30L)).thenReturn("Y");
            ResponseStatusException conflict = assertThrows(ResponseStatusException.class,
                    () -> service.submitMemberConsent(GROUP_ID, REQUEST_ID, 30L, form));
            assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        }

        @Test
        void submitConsentRecordsOrderConsentAuditLog() {
            when(mapper.lockGroupForOrderUpdate(GROUP_ID)).thenReturn(GROUP_ID);
            when(mapper.selectGroupInfo(GROUP_ID)).thenReturn(groupInfo(OWNER_ID, "ACTIVE", 3));
            when(mapper.countActiveMember(GROUP_ID, 30L)).thenReturn(1);
            when(mapper.selectPayoutRequestForUpdate(GROUP_ID, REQUEST_ID))
                    .thenReturn(payoutRequest("CONSENT_PENDING"));
            when(mapper.selectMemberConsentStatus(GROUP_ID, REQUEST_ID, 30L)).thenReturn("P");
            when(mapper.updateMemberConsent(GROUP_ID, REQUEST_ID, 30L, "Y")).thenReturn(1);

            PayoutOrderDTO.ConsentForm form = new PayoutOrderDTO.ConsentForm();
            form.setAgreedYn("Y");

            service.submitMemberConsent(GROUP_ID, REQUEST_ID, 30L, form);

            verify(mapper).insertAuditLog(
                    eq(GROUP_ID), eq(30L), eq("ORDER_CONSENT"),
                    eq("수령 순서 변경 동의 응답: Y (신청번호: " + REQUEST_ID + ")"));
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
                    OWNER_ID, "WITHDRAW_REQUEST", "중도 탈퇴 재확인 요청",
                    "탈퇴 신청 회원이 정산 및 탈퇴 처리 재확인을 요청했습니다.",
                    "/procedures?groupId=" + GROUP_ID, REQUEST_ID);
            verify(mapper).insertAuditLog(
                    GROUP_ID, MEMBER_ID, MEMBER_ID, "WITHDRAW_RECONFIRM",
                    "중도 탈퇴 재확인 요청 (신청번호: " + REQUEST_ID + ")");
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

        var response = controller.getGroupDissolution(GROUP_ID, authentication);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
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
