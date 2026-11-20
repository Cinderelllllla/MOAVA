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
