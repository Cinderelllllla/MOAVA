package com.moava.group.procedures.service;

import com.moava.group.procedures.dto.WithdrawalDTO;
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
        when(withdrawalMapper.selectGroupInfo(GROUP_ID)).thenReturn(groupInfo());
        when(withdrawalMapper.selectWithdrawalByIdForUpdate(GROUP_ID, REQUEST_ID)).thenReturn(consentPendingRequest());
        when(withdrawalMapper.selectGyeMemberInfoForUpdate(GROUP_ID, APPLICANT_ID))
                .thenReturn(memberInfo("ACTIVE"));
        when(withdrawalMapper.countSettlementConsentTargets(REQUEST_ID)).thenReturn(2);
        when(withdrawalMapper.countUnresolvedActiveSettlementConsentTargets(GROUP_ID, REQUEST_ID)).thenReturn(1);

        assertThrows(IllegalStateException.class,
                () -> withdrawalService.approveWithdrawal(GROUP_ID, REQUEST_ID, OWNER_ID));

        verify(withdrawalMapper, never()).updateWithdrawalStatus(
                eq(GROUP_ID), eq(REQUEST_ID), eq("CONSENT_PENDING"), eq("APPROVED"));
    }

    @Test
    void approveRejectsLegacyRequestWithoutFixedTargets() {
        when(withdrawalMapper.selectGroupInfo(GROUP_ID)).thenReturn(groupInfo());
        when(withdrawalMapper.selectWithdrawalByIdForUpdate(GROUP_ID, REQUEST_ID)).thenReturn(consentPendingRequest());
        when(withdrawalMapper.selectGyeMemberInfoForUpdate(GROUP_ID, APPLICANT_ID))
                .thenReturn(memberInfo("ACTIVE"));
        when(withdrawalMapper.countSettlementConsentTargets(REQUEST_ID)).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> withdrawalService.approveWithdrawal(GROUP_ID, REQUEST_ID, OWNER_ID));

        verify(withdrawalMapper, never()).updateWithdrawalStatus(
                eq(GROUP_ID), eq(REQUEST_ID), eq("CONSENT_PENDING"), eq("APPROVED"));
    }

    @Test
    void exemptionStoresReasonAndAdminAudit() {
        when(withdrawalMapper.isActiveAdmin(ADMIN_ID)).thenReturn(1);
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
        when(withdrawalMapper.selectGroupInfo(GROUP_ID)).thenReturn(groupInfo("DISSOLVED"));

        assertThrows(IllegalStateException.class,
                () -> withdrawalService.requestWithdrawal(GROUP_ID, APPLICANT_ID, validApplyForm()));

        verify(withdrawalMapper, never()).insertWithdrawalRequest(
                eq(GROUP_ID), eq(APPLICANT_ID), anyString(), anyString(), anyString());
    }

    @Test
    void registerSettlementRejectsGroupThatIsNotActive() {
        when(withdrawalMapper.selectGroupInfo(GROUP_ID)).thenReturn(groupInfo("DISSOLVE_PENDING"));
        WithdrawalDTO.SettlementForm form = new WithdrawalDTO.SettlementForm();
        form.setSettlementAmount(10000L);
        form.setSettlementMemo("정산 근거");

        assertThrows(IllegalStateException.class,
                () -> withdrawalService.registerSettlementPlan(GROUP_ID, REQUEST_ID, OWNER_ID, form));

        verify(withdrawalMapper, never()).updateSettlementPlan(
                eq(GROUP_ID), eq(REQUEST_ID), anyString(), eq(10000L), anyString(), eq(OWNER_ID));
    }

    private Map<String, Object> groupInfo() {
        return Map.of("OWNER_ID", OWNER_ID);
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
