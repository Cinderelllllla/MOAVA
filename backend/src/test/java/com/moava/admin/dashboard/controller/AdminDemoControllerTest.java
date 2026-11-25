package com.moava.admin.dashboard.controller;

import com.moava.admin.dashboard.dto.AdminDashboardDTO;
import com.moava.admin.dashboard.service.AdminDemoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminDemoControllerTest {

    @Mock
    private AdminDemoService demoService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminDemoController(demoService))
                .setControllerAdvice(new AdminDashboardExceptionHandler())
                .build();
    }

    private static Authentication auth(String subject, String role) {
        return new UsernamePasswordAuthenticationToken(subject, null, List.of(new SimpleGrantedAuthority(role)));
    }

    @Test
    @DisplayName("POST /api/v1/admin/demo/test-members: 관리자 요청 시 테스트 회원 5명 생성 위임")
    void createTestMembers_allowsAdmin() throws Exception {
        AdminDashboardDTO.TestMembersResponse response = AdminDashboardDTO.TestMembersResponse.builder()
                .createdCount(5)
                .createdEmails(List.of("testuser1@moava.test", "testuser2@moava.test"))
                .message("시연용 테스트 회원 5명을 생성했습니다. (초기 비밀번호: Test1234!)")
                .build();
        when(demoService.createFiveTestMembers(1L)).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/demo/test-members").principal(auth("1", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(5))
                .andExpect(jsonPath("$.createdEmails[0]").value("testuser1@moava.test"));

        verify(demoService).createFiveTestMembers(1L);
    }

    @Test
    @DisplayName("POST /api/v1/admin/demo/reset: 관리자 요청 시 시연 데이터 초기화 위임")
    void resetDemoData_allowsAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/demo/reset").principal(auth("2", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("회원·모임·정책 시연 데이터를 모두 초기화했습니다."));

        verify(demoService).resetAllDemoData(2L);
    }

    @Test
    @DisplayName("POST /api/v1/admin/demo/**: 일반 회원 403 및 비로그인 401 차단")
    void demoEndpoints_enforceAdminAuth() throws Exception {
        mockMvc.perform(post("/api/v1/admin/demo/test-members").principal(auth("10", "ROLE_MEMBER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/admin/demo/reset"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verifyNoInteractions(demoService);
    }
}
