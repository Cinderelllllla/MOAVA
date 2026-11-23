package com.moava.group.procedures.service;

import com.moava.group.procedures.dto.WithdrawalDTO;
import com.moava.group.procedures.exception.ProcedureException;
import com.moava.group.procedures.mapper.WithdrawalMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceTest {
    private static final Long GROUP_ID = 1L;
    private static final Long OWNER_ID = 10L;
    private static final Long APPLICANT_ID = 20L;
    private static final Long REQUEST_ID = 100L;
    private static final Long ADMIN_ID = 7L;
    private static final Long TARGET_ID = 30L;

    @Mock
    private WithdrawalMapper withdrawalMapper;

    @InjectMocks
    private WithdrawalService withdrawalService;

    @Test
    void approveRequiresSnapshotAndAllCurrentTargetsResolved() {
        when(withdrawalMapper.selectGroupInfoForUpdate(GROUP_ID)).thenReturn(groupInfo());
        when(withdrawalMapper.selectWithdrawalByIdForUpdate(GROUP_ID, REQUEST_ID)).thenReturn(consentPendingRequest());
        when(withdrawalMapper.selectGyeMemberInfoForUpdate(GROUP_ID, APPLICANT_ID))
                .thenReturn(memberInfo("ACTIVE"));
        when(withdrawalMapper.countSettlementConsentTargets(REQUEST_ID)).thenReturn(2);
        when(withdrawalMapper.countUnresolvedActiveSettlementConsentTargets(GROUP_ID, REQUEST_ID)).thenReturn(1);

        assertThrows(ProcedureException.class,
                () -> withdrawalService.approveWithdrawal(GROUP_ID, REQUEST_ID, OWNER_ID));

        verify(withdrawalMapper, never()).updateWithdrawalStatus(
                eq(GROUP_ID), eq(REQUEST_ID), eq("CONSENT_PENDING"), eq("APPROVED"));
    }

    @Test
    void approveRejectsLegacyRequestWithoutFixedTargets() {
        when(withdrawalMapper.selectGroupInfoForUpdate(GROUP_ID)).thenReturn(groupInfo());
        when(withdrawalMapper.selectWithdrawalByIdForUpdate(GROUP_ID, REQUEST_ID)).thenReturn(consentPendingRequest());
        when(withdrawalMapper.selectGyeMemberInfoForUpdate(GROUP_ID, APPLICANT_ID))
                .thenReturn(memberInfo("ACTIVE"));
        when(withdrawalMapper.countSettlementConsentTargets(REQUEST_ID)).thenReturn(0);

        assertThrows(ProcedureException.class,
                () -> withdrawalService.approveWithdrawal(GROUP_ID, REQUEST_ID, OWNER_ID));

        verify(withdrawalMapper, never()).updateWithdrawalStatus(
                eq(GROUP_ID), eq(REQUEST_ID), eq("CONSENT_PENDING"), eq("APPROVED"));
    }

    @Test
    void exemptionStoresReasonAndAdminAudit() {
        when(withdrawalMapper.isActiveAdmin(ADMIN_ID)).thenReturn(1);
        when(withdrawalMapper.selectGroupInfoForUpdate(GROUP_ID)).thenReturn(groupInfo());
        when(withdrawalMapper.selectWithdrawalByIdForUpdate(GROUP_ID, REQUEST_ID)).thenReturn(consentPendingRequest());
        when(withdrawalMapper.exemptSettlementConsentTarget(
                GROUP_ID, REQUEST_ID, TARGET_ID, ADMIN_ID, "연락 두절")).thenReturn(1);

        withdrawalService.exemptSettlementConsentTarget(
                GROUP_ID, REQUEST_ID, TARGET_ID, ADMIN_ID, "연락 두절");

        verify(withdrawalMapper).insertAdminAuditLog(
                eq(GROUP_ID), eq(ADMIN_ID), eq(TARGET_ID), eq("WITHDRAW_CONSENT_EXEMPT"), anyString());
    }

    @Test
    void requestWithdrawalRejectsGroupThatIsNotActive() {
        when(withdrawalMapper.selectGroupInfoForUpdate(GROUP_ID)).thenReturn(groupInfo("DISSOLVED"));

        assertThrows(ProcedureException.class,
                () -> withdrawalService.requestWithdrawal(GROUP_ID, APPLICANT_ID, validApplyForm()));

        verify(withdrawalMapper, never()).insertWithdrawalRequest(
                eq(GROUP_ID), eq(APPLICANT_ID), anyString(), anyString(), anyString());
    }

    @Test
    void registerSettlementRejectsGroupThatIsNotActive() {
        when(withdrawalMapper.selectGroupInfoForUpdate(GROUP_ID)).thenReturn(groupInfo("DISSOLVE_PENDING"));
        WithdrawalDTO.SettlementForm form = new WithdrawalDTO.SettlementForm();
        form.setSettlementAmount(10000L);
        form.setSettlementMemo("정산 근거");

        assertThrows(ProcedureException.class,
                () -> withdrawalService.registerSettlementPlan(GROUP_ID, REQUEST_ID, OWNER_ID, form));

        verify(withdrawalMapper, never()).updateSettlementPlan(
                eq(GROUP_ID), eq(REQUEST_ID), anyString(), eq(10000L), anyString(), eq(OWNER_ID));
    }

    @Test
    void requestWithdrawalLocksGroupBeforeCheckingDuplicateRequests() {
        when(withdrawalMapper.selectGroupInfoForUpdate(GROUP_ID)).thenReturn(groupInfo());
        when(withdrawalMapper.selectGyeMemberInfo(GROUP_ID, APPLICANT_ID))
                .thenReturn(Map.of("STATUS", "ACTIVE", "PAYOUT_RECEIVED_YN", "N"));
        when(withdrawalMapper.countActiveWithdrawalByMember(GROUP_ID, APPLICANT_ID)).thenReturn(1);

        assertThrows(ProcedureException.class,
                () -> withdrawalService.requestWithdrawal(GROUP_ID, APPLICANT_ID, validApplyForm()));

        var calls = inOrder(withdrawalMapper);
        calls.verify(withdrawalMapper).selectGroupInfoForUpdate(GROUP_ID);
        calls.verify(withdrawalMapper).countActiveWithdrawalByMember(GROUP_ID, APPLICANT_ID);
        verify(withdrawalMapper, never()).insertWithdrawalRequest(
                eq(GROUP_ID), eq(APPLICANT_ID), anyString(), anyString(), anyString());
    }

    @Test
    void registerSettlementRejectsReactivatingRejectedRequestWhenAnotherActiveRequestExists() {
        Map<String, Object> rejectedReq = consentPendingRequest();
        rejectedReq.put("STATUS", "REJECTED");
        when(withdrawalMapper.selectGroupInfoForUpdate(GROUP_ID)).thenReturn(groupInfo());
        when(withdrawalMapper.selectWithdrawalByIdForUpdate(GROUP_ID, REQUEST_ID)).thenReturn(rejectedReq);
        when(withdrawalMapper.selectGyeMemberInfoForUpdate(GROUP_ID, APPLICANT_ID))
                .thenReturn(memberInfo("ACTIVE"));
        when(withdrawalMapper.countActiveWithdrawalByMember(GROUP_ID, APPLICANT_ID)).thenReturn(1);

        WithdrawalDTO.SettlementForm form = new WithdrawalDTO.SettlementForm();
        form.setSettlementAmount(50_000L);
        form.setSettlementMemo("재정산 근거");

        ProcedureException error = assertThrows(ProcedureException.class,
                () -> withdrawalService.registerSettlementPlan(GROUP_ID, REQUEST_ID, OWNER_ID, form));

        org.junit.jupiter.api.Assertions.assertEquals(
                org.springframework.http.HttpStatus.CONFLICT, error.getStatus());
        verify(withdrawalMapper, never()).updateSettlementPlan(
                eq(GROUP_ID), eq(REQUEST_ID), anyString(), eq(50_000L), anyString(), eq(OWNER_ID));
    }

    @Test
    void registerSettlementRejectsInactiveApplicantAndOutOfRangeAmount() {
        when(withdrawalMapper.selectGroupInfoForUpdate(GROUP_ID)).thenReturn(groupInfo());

        WithdrawalDTO.SettlementForm overflowForm = new WithdrawalDTO.SettlementForm();
        overflowForm.setSettlementAmount(1_000_000_000_000L);
        overflowForm.setSettlementMemo("범위 초과");

        ProcedureException badRequest = assertThrows(ProcedureException.class,
                () -> withdrawalService.registerSettlementPlan(GROUP_ID, REQUEST_ID, OWNER_ID, overflowForm));
        org.junit.jupiter.api.Assertions.assertEquals(
                org.springframework.http.HttpStatus.BAD_REQUEST, badRequest.getStatus());

        when(withdrawalMapper.selectWithdrawalByIdForUpdate(GROUP_ID, REQUEST_ID))
                .thenReturn(consentPendingRequest());
        when(withdrawalMapper.selectGyeMemberInfoForUpdate(GROUP_ID, APPLICANT_ID))
                .thenReturn(memberInfo("WITHDRAWN"));

        WithdrawalDTO.SettlementForm validForm = new WithdrawalDTO.SettlementForm();
        validForm.setSettlementAmount(10_000L);
        validForm.setSettlementMemo("정산 근거");

        ProcedureException conflict = assertThrows(ProcedureException.class,
                () -> withdrawalService.registerSettlementPlan(GROUP_ID, REQUEST_ID, OWNER_ID, validForm));
        org.junit.jupiter.api.Assertions.assertEquals(
                org.springframework.http.HttpStatus.CONFLICT, conflict.getStatus());
    }

    @Test
    void approveRejectsWhenApplicantHasUnconfirmedProofs() {
        when(withdrawalMapper.selectGroupInfoForUpdate(GROUP_ID)).thenReturn(groupInfo());
        when(withdrawalMapper.selectWithdrawalByIdForUpdate(GROUP_ID, REQUEST_ID))
                .thenReturn(consentPendingRequest());
        when(withdrawalMapper.selectGyeMemberInfoForUpdate(GROUP_ID, APPLICANT_ID))
                .thenReturn(memberInfo("ACTIVE"));
        when(withdrawalMapper.countUnconfirmedProofsByMember(GROUP_ID, APPLICANT_ID)).thenReturn(1);

        ProcedureException error = assertThrows(ProcedureException.class,
                () -> withdrawalService.approveWithdrawal(GROUP_ID, REQUEST_ID, OWNER_ID));

        org.junit.jupiter.api.Assertions.assertEquals(
                org.springframework.http.HttpStatus.CONFLICT, error.getStatus());
        verify(withdrawalMapper, never()).updateWithdrawalStatus(
                eq(GROUP_ID), eq(REQUEST_ID), eq("CONSENT_PENDING"), eq("APPROVED"));
    }

    @Test
    void approveRejectsOpenPayoutRequestsAndSendsWithdrawDoneNotification() {
        when(withdrawalMapper.selectGroupInfoForUpdate(GROUP_ID)).thenReturn(groupInfo());
        when(withdrawalMapper.selectWithdrawalByIdForUpdate(GROUP_ID, REQUEST_ID))
                .thenReturn(consentPendingRequest());
        when(withdrawalMapper.selectGyeMemberInfoForUpdate(GROUP_ID, APPLICANT_ID))
                .thenReturn(memberInfo("ACTIVE"));
        when(withdrawalMapper.countUnconfirmedProofsByMember(GROUP_ID, APPLICANT_ID)).thenReturn(0);
        when(withdrawalMapper.countSettlementConsentTargets(REQUEST_ID)).thenReturn(2);
        when(withdrawalMapper.countUnresolvedActiveSettlementConsentTargets(GROUP_ID, REQUEST_ID)).thenReturn(0);
        when(withdrawalMapper.updateWithdrawalStatus(GROUP_ID, REQUEST_ID, "CONSENT_PENDING", "APPROVED"))
                .thenReturn(1);
        when(withdrawalMapper.updateGyeMemberWithdrawn(GROUP_ID, APPLICANT_ID)).thenReturn(1);

        withdrawalService.approveWithdrawal(GROUP_ID, REQUEST_ID, OWNER_ID);

        verify(withdrawalMapper).rejectOpenPayoutRequestsByMember(GROUP_ID, APPLICANT_ID);
        verify(withdrawalMapper).insertNotification(
                APPLICANT_ID,
                "WITHDRAW_DONE",
                "중도 탈퇴 최종 승인 안내",
                "전원 정산안 동의가 완료되어 모임 중도 탈퇴가 최종 승인되었습니다.",
                "/my-groups",
                REQUEST_ID
        );
    }

    @Test
    void submitConsentRejectionNotifiesApplicantAndLeaderWithConsentResult() {
        when(withdrawalMapper.selectGroupInfoForUpdate(GROUP_ID)).thenReturn(groupInfo());
        when(withdrawalMapper.selectWithdrawalByIdForUpdate(GROUP_ID, REQUEST_ID))
                .thenReturn(consentPendingRequest());
        when(withdrawalMapper.selectGyeMemberInfoForUpdate(GROUP_ID, TARGET_ID))
                .thenReturn(memberInfo("ACTIVE"));
        when(withdrawalMapper.updateSettlementConsentTarget(GROUP_ID, REQUEST_ID, TARGET_ID, "REJECTED"))
                .thenReturn(1);
        when(withdrawalMapper.updateWithdrawalStatus(GROUP_ID, REQUEST_ID, "CONSENT_PENDING", "REJECTED"))
                .thenReturn(1);

        WithdrawalDTO.ConsentForm form = new WithdrawalDTO.ConsentForm();
        form.setAgreedYn("N");

        withdrawalService.submitSettlementConsent(GROUP_ID, REQUEST_ID, TARGET_ID, form);

        verify(withdrawalMapper).insertNotification(
                APPLICANT_ID,
                "CONSENT_RESULT",
                "탈퇴 정산안 결과 안내",
                "탈퇴 정산안에 반대가 발생하여 정산안 재조정이 필요합니다.",
                "/procedures?groupId=" + GROUP_ID,
                REQUEST_ID
        );
        verify(withdrawalMapper).insertNotification(
                OWNER_ID,
                "CONSENT_RESULT",
                "탈퇴 정산안 반대 발생",
                "중도 탈퇴 정산안에 반대가 발생했습니다. 정산안을 재조정해 주세요.",
                "/procedures?groupId=" + GROUP_ID,
                REQUEST_ID
        );
    }

    private Map<String, Object> groupInfo() {
        return Map.of("OWNER_ID", OWNER_ID, "STATUS", "ACTIVE");
    }

    private Map<String, Object> groupInfo(String status) {
        return Map.of("OWNER_ID", OWNER_ID, "STATUS", status);
    }

    private WithdrawalDTO.ApplyForm validApplyForm() {
        WithdrawalDTO.ApplyForm form = new WithdrawalDTO.ApplyForm();
        form.setDetail("개인 사정");
        form.setReasonType("PERSONAL");
        return form;
    }

    private Map<String, Object> consentPendingRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("GROUP_ID", GROUP_ID);
        request.put("MEMBER_ID", APPLICANT_ID);
        request.put("STATUS", "CONSENT_PENDING");
        request.put("SETTLEMENT_AMOUNT", 10000L);
        request.put("SETTLEMENT_AT", new Date());
        return request;
    }

    private Map<String, Object> memberInfo(String status) {
        return Map.of("STATUS", status);
    }
}
