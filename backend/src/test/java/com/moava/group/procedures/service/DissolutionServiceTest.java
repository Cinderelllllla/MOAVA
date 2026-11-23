package com.moava.group.procedures.service;

import com.moava.group.procedures.dto.DissolutionDTO;
import com.moava.group.procedures.mapper.DissolutionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DissolutionServiceTest {
    private static final Long GROUP_ID = 1L;
    private static final Long OWNER_ID = 10L;
    private static final Long ADMIN_ID = 7L;

    @Mock
    private DissolutionMapper dissolutionMapper;

    @InjectMocks
    private DissolutionService dissolutionService;

    @Test
    void requestRejectsAuthenticatedUserWhoIsNotOwner() {
        when(dissolutionMapper.lockGroupForDissolution(GROUP_ID)).thenReturn(groupInfo("ACTIVE"));

        assertThrows(ResponseStatusException.class,
                () -> dissolutionService.requestDissolution(GROUP_ID, 20L, validRequest()));

        verify(dissolutionMapper, never()).insertDissolutionRequest(GROUP_ID, 20L, "해산 사유");
        verify(dissolutionMapper, never()).updateGroupStatus(GROUP_ID, "ACTIVE", "DISSOLVE_PENDING");
    }

    @Test
    void requestRejectsActivePayoutRequest() {
        when(dissolutionMapper.lockGroupForDissolution(GROUP_ID)).thenReturn(groupInfo("ACTIVE"));
        when(dissolutionMapper.selectPendingDissolutionByGroup(GROUP_ID)).thenReturn(null);
        when(dissolutionMapper.countActiveWithdrawals(GROUP_ID)).thenReturn(0);
        when(dissolutionMapper.countActivePayoutRequests(GROUP_ID)).thenReturn(1);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> dissolutionService.requestDissolution(GROUP_ID, OWNER_ID, validRequest()));

        assertEquals(409, error.getStatusCode().value());
        verify(dissolutionMapper, never()).insertDissolutionRequest(GROUP_ID, OWNER_ID, "해산 사유");
    }

    @Test
    void approveRejectsGroupThatIsNotDissolvePending() {
        when(dissolutionMapper.countActiveAdmin(ADMIN_ID)).thenReturn(1);
        when(dissolutionMapper.lockGroupForDissolution(GROUP_ID)).thenReturn(groupInfo("ACTIVE"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> dissolutionService.approveDissolutionByAdmin(GROUP_ID, ADMIN_ID));

        assertEquals(409, error.getStatusCode().value());
        verify(dissolutionMapper, never()).updateDissolutionDecision(GROUP_ID, 100L, "APPROVED", ADMIN_ID);
    }

    @Test
    void rejectRejectsGroupThatIsNotDissolvePending() {
        when(dissolutionMapper.countActiveAdmin(ADMIN_ID)).thenReturn(1);
        when(dissolutionMapper.lockGroupForDissolution(GROUP_ID)).thenReturn(groupInfo("ACTIVE"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> dissolutionService.rejectDissolutionByAdmin(GROUP_ID, ADMIN_ID, "증빙 미충족"));

        assertEquals(409, error.getStatusCode().value());
        verify(dissolutionMapper, never()).updateDissolutionDecision(GROUP_ID, 100L, "REJECTED", ADMIN_ID);
    }

    @Test
    void adminListRejectsInactiveAdmin() {
        when(dissolutionMapper.countActiveAdmin(ADMIN_ID)).thenReturn(0);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> dissolutionService.getAdminDissolutionList(ADMIN_ID, "PENDING"));

        assertEquals(403, error.getStatusCode().value());
        verify(dissolutionMapper, never()).selectAdminDissolutionList(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void adminListRejectsInvalidStatusFilterAndNormalizesValidStatus() {
        when(dissolutionMapper.countActiveAdmin(ADMIN_ID)).thenReturn(1);

        ResponseStatusException invalid = assertThrows(ResponseStatusException.class,
                () -> dissolutionService.getAdminDissolutionList(ADMIN_ID, "UNKNOWN"));
        assertEquals(400, invalid.getStatusCode().value());

        DissolutionDTO.StatusResponse item = new DissolutionDTO.StatusResponse();
        item.setMemberCount(4);
        item.setTotalConfirmedAmount(400_000L);
        when(dissolutionMapper.selectAdminDissolutionList("PENDING")).thenReturn(java.util.List.of(item));

        var list = dissolutionService.getAdminDissolutionList(ADMIN_ID, " pending ");

        assertEquals(1, list.size());
        assertEquals(100_000L, list.get(0).getPerMemberRefundAmount());
        org.junit.jupiter.api.Assertions.assertNotNull(list.get(0).getSettlementNotice());
    }

    @Test
    void approveSendsDissolvedNotificationWithoutMisleadingPerMemberAmount() {
        when(dissolutionMapper.countActiveAdmin(ADMIN_ID)).thenReturn(1);
        when(dissolutionMapper.lockGroupForDissolution(GROUP_ID)).thenReturn(groupInfo("DISSOLVE_PENDING"));
        when(dissolutionMapper.selectPendingDissolutionByGroup(GROUP_ID))
                .thenReturn(Map.of("DISSOLUTION_REQUEST_ID", 100L, "REQUESTED_BY", OWNER_ID));
        when(dissolutionMapper.countUnconfirmedProofs(GROUP_ID)).thenReturn(0);
        when(dissolutionMapper.countActiveWithdrawals(GROUP_ID)).thenReturn(0);
        when(dissolutionMapper.countActivePayoutRequests(GROUP_ID)).thenReturn(0);
        when(dissolutionMapper.updateDissolutionDecision(GROUP_ID, 100L, "APPROVED", ADMIN_ID)).thenReturn(1);
        when(dissolutionMapper.updateGroupStatus(GROUP_ID, "DISSOLVE_PENDING", "DISSOLVED")).thenReturn(1);
        when(dissolutionMapper.selectActiveMemberIds(GROUP_ID)).thenReturn(java.util.List.of(OWNER_ID, 20L));
        when(dissolutionMapper.selectTotalConfirmedProofAmount(GROUP_ID)).thenReturn(300_000L);

        String message = dissolutionService.approveDissolutionByAdmin(GROUP_ID, ADMIN_ID);

        assertEquals("해산을 승인했어요.", message);
        String expectedBody = "[테스트 모임] 모임 해산이 최종 승인되었습니다. "
                + "실제 환급·정산 금액은 이미 지급된 곗돈과 중도 탈퇴 정산 내역을 반영해 모임 정산 안내에서 최종 확인해 주세요.";
        verify(dissolutionMapper).insertNotification(
                OWNER_ID, "DISSOLVED", "모임 해산 완료 · 정산 안내", expectedBody, "/my-groups", 100L);
        verify(dissolutionMapper).insertNotification(
                20L, "DISSOLVED", "모임 해산 완료 · 정산 안내", expectedBody, "/my-groups", 100L);
        verify(dissolutionMapper).insertAdminAuditLog(
                GROUP_ID, ADMIN_ID, "DISSOLVE_APPROVE",
                "계모임 해산 최종 승인 완료 (확인 납입 총액 참고치: 300,000원, 활성 회원 2명 — 실제 정산액 별도 확인)");
    }

    private DissolutionDTO.RequestForm validRequest() {
        DissolutionDTO.RequestForm form = new DissolutionDTO.RequestForm();
        form.setReason("해산 사유");
        return form;
    }

    private Map<String, Object> groupInfo(String status) {
        Map<String, Object> group = new HashMap<>();
        group.put("GROUP_ID", GROUP_ID);
        group.put("OWNER_ID", OWNER_ID);
        group.put("TITLE", "테스트 모임");
        group.put("STATUS", status);
        return group;
    }
}
