package com.moava.admin.dashboard.controller;

import com.moava.admin.dashboard.dto.AdminDashboardDTO;
import com.moava.admin.dashboard.service.AdminDashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminDashboardControllerTest {

    @Mock
    private AdminDashboardService dashboardService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminDashboardController(dashboardService))
                .setControllerAdvice(new AdminDashboardExceptionHandler())
                .build();
    }

    private static Authentication auth(String subject, String role) {
        return new UsernamePasswordAuthenticationToken(subject, null, List.of(new SimpleGrantedAuthority(role)));
    }

    @Test
    @DisplayName("GET /api/v1/admin/dashboard: ROLE_ADMIN 정상 조회")
    void getDashboard_allowsAdmin() throws Exception {
        AdminDashboardDTO.SummaryStats stats = new AdminDashboardDTO.SummaryStats();
        stats.setTotalMembers(12);
        stats.setTotalGroups(4);
        stats.setPendingDissolutions(1);
        stats.setBannedMembers(2);

        AdminDashboardDTO.DashboardResponse response = AdminDashboardDTO.DashboardResponse.builder()
                .stats(stats)
                .recentLogs(List.of(
                        AdminDashboardDTO.RecentAuditItem.builder()
                                .auditLogId(100L)
                                .groupTitle("플랫폼 공통")
                                .eventType("BAN")
                                .actorLabel("관리자 운영자")
                                .description("플랫폼 가입 제한")
                                .createdAt("2026-11-25 10:00")
                                .build()
                ))
                .demoEnabled(true)
                .build();
        when(dashboardService.getDashboardSummary(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/dashboard").principal(auth("1", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.totalMembers").value(12))
                .andExpect(jsonPath("$.stats.totalGroups").value(4))
                .andExpect(jsonPath("$.stats.pendingDissolutions").value(1))
                .andExpect(jsonPath("$.stats.bannedMembers").value(2))
                .andExpect(jsonPath("$.recentLogs[0].eventType").value("BAN"))
                .andExpect(jsonPath("$.demoEnabled").value(true));

        verify(dashboardService).getDashboardSummary(1L);
    }

    @Test
    @DisplayName("GET /api/v1/admin/dashboard: ROLE_MEMBER 호출 시 403 차단")
    void getDashboard_blocksMember() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard").principal(auth("10", "ROLE_MEMBER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(dashboardService);
    }

    @Test
    @DisplayName("GET /api/v1/admin/dashboard: 비로그인 또는 익명 요청 시 401 차단")
    void getDashboard_blocksUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        Authentication anon = new AnonymousAuthenticationToken(
                "k", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        mockMvc.perform(get("/api/v1/admin/dashboard").principal(anon))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verifyNoInteractions(dashboardService);
    }
}
