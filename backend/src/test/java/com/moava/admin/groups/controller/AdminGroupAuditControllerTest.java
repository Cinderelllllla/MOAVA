package com.moava.admin.groups.controller;

import com.moava.admin.groups.dto.AdminGroupAuditDTO;
import com.moava.admin.groups.service.AdminGroupAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F38 AdminGroupAuditController 단위 테스트.
 * - 권한 검증(ROLE_ADMIN만 허용, 회원 403, 비로그인 401)
 * - 기본값(page=0, size=8, reviewStatus=ALL, adminOnly=false) 및 필터 조합 전달 검증
 */
@ExtendWith(MockitoExtension.class)
class AdminGroupAuditControllerTest {

    @Mock
    AdminGroupAuditService service;

    @InjectMocks
    AdminGroupAuditController controller;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AdminGroupAuditExceptionHandler())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @Test
    @DisplayName("모임 목록: 관리자 토큰이면 200 + ApiResponse 반환")
    void getGroups_admin_200() throws Exception {
        when(service.getGroups(1L)).thenReturn(List.of(
                AdminGroupAuditDTO.GroupSummaryItem.builder()
                        .groupId(101L)
                        .title("마포 목돈 모임")
                        .ownerName("김○늘")
                        .status("ACTIVE")
                        .activeMemberCount(4)
                        .maxMembers(6)
                        .build()
        ));

        mockMvc.perform(get("/api/v1/admin/groups").principal(token("1", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].groupId").value(101))
                .andExpect(jsonPath("$.data[0].ownerName").value("김○늘"))
                .andExpect(jsonPath("$.meta.requestId").exists());

        verify(service).getGroups(1L);
    }

    @Test
    @DisplayName("모임 목록: 일반 회원 토큰이면 403 FORBIDDEN")
    void getGroups_member_403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/groups").principal(token("1", "ROLE_MEMBER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verify(service, never()).getGroups(anyLong());
    }

    @Test
    @DisplayName("모임 목록: 비로그인이면 401 UNAUTHORIZED")
    void getGroups_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/groups"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verify(service, never()).getGroups(anyLong());
    }

    @Test
    @DisplayName("모임 상세: 잘못된 groupId 경로 타입이면 400 BAD_REQUEST")
    void getGroupDetail_badPathType_400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/groups/abc").principal(token("1", "ROLE_ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verify(service, never()).getGroupDetail(anyLong(), anyLong());
    }

    @Test
    @DisplayName("감사 이력: 파라미터 생략 시 기본값(reviewStatus=ALL, adminOnly=false, page=0, size=8) 전달")
    void getAuditLogs_defaults_200() throws Exception {
        when(service.getAuditLogs(1L, null, null, "ALL", false, null, 0, 8))
                .thenReturn(AdminGroupAuditDTO.AuditLogPageResponse.builder()
                        .items(List.of())
                        .page(0)
                        .size(8)
                        .totalCount(0)
                        .totalPages(0)
                        .hasPrev(false)
                        .hasNext(false)
                        .build());

        mockMvc.perform(get("/api/v1/admin/audit-logs").principal(token("1", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(8))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.meta.requestId").exists());

        verify(service).getAuditLogs(eq(1L), isNull(), isNull(), eq("ALL"), eq(false), isNull(), eq(0), eq(8));
    }

    @Test
    @DisplayName("감사 이력: 필터 3종 + v3.1 관리자 필터 + 페이지 파라미터 모두 전달")
    void getAuditLogs_allFilters_200() throws Exception {
        when(service.getAuditLogs(1L, 101L, "DISSOLVE_APPROVE", "DONE", true, 3L, 2, 8))
                .thenReturn(AdminGroupAuditDTO.AuditLogPageResponse.builder()
                        .items(List.of())
                        .page(2)
                        .size(8)
                        .totalCount(20)
                        .totalPages(3)
                        .hasPrev(true)
                        .hasNext(false)
                        .build());

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("groupId", "101")
                        .param("eventType", "DISSOLVE_APPROVE")
                        .param("reviewStatus", "DONE")
                        .param("adminOnly", "true")
                        .param("actorAdminId", "3")
                        .param("page", "2")
                        .param("size", "8")
                        .principal(token("1", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(20))
                .andExpect(jsonPath("$.data.totalPages").value(3));

        verify(service).getAuditLogs(1L, 101L, "DISSOLVE_APPROVE", "DONE", true, 3L, 2, 8);
    }

    @Test
    @DisplayName("감사 이력: 일반 회원 토큰이면 403 FORBIDDEN")
    void getAuditLogs_member_403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs").principal(token("1", "ROLE_MEMBER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verify(service, never()).getAuditLogs(anyLong(), any(), any(), any(), anyBoolean(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("감사 이력 메타 옵션: 관리자 200 반환")
    void getAuditFilterOptions_admin_200() throws Exception {
        when(service.getFilterOptions(1L)).thenReturn(
                AdminGroupAuditDTO.AuditFilterOptionsResponse.builder()
                        .eventTypes(List.of("DISSOLVE_APPROVE", "JOIN_REQUEST"))
                        .admins(List.of(AdminGroupAuditDTO.AdminOption.builder()
                                .adminId(1L).name("관리자").email("admin@moava.com").status("ACTIVE").build()))
                        .build()
        );

        mockMvc.perform(get("/api/v1/admin/audit-logs/meta").principal(token("1", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventTypes[0]").value("DISSOLVE_APPROVE"))
                .andExpect(jsonPath("$.data.admins[0].adminId").value(1));
    }

    private static UsernamePasswordAuthenticationToken token(String id, String role) {
        return new UsernamePasswordAuthenticationToken(id, null, List.of(new SimpleGrantedAuthority(role)));
    }
}
