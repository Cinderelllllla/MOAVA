package com.moava.overdue.controller;

import com.moava.overdue.dto.OverdueActionRequest;
import com.moava.overdue.service.OverdueAdminService;
import com.moava.proofs.controller.ProofExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OverdueController 단위 테스트 (F26) — 관리자 토큰 확인, 기본값, 잘못된 요청 400
 */
@ExtendWith(MockitoExtension.class)
class OverdueControllerTest {

    @Mock
    OverdueAdminService overdueAdminService;      // 가짜 Service

    @InjectMocks
    OverdueController overdueController;          // 진짜 Controller

    MockMvc mockMvc;

    static final String LIST_URL = "/api/v1/admin/overdue-cases";
    static final String ACTION_URL = "/api/v1/admin/overdue-cases/10/actions";
    static final String WARN_BODY = "{\"action\":\"WARN\",\"reason\":\"11월분 미납\"}";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(overdueController)
                .setControllerAdvice(new ProofExceptionHandler())             // 오류 응답 형식
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)   // 한글 메시지가 깨지지 않게
                .build();
    }

    // ---------- 목록 GET ----------

    @Test
    @DisplayName("목록: status를 안 주면 OPEN으로 Service에 넘김 → 200")
    void list_defaultOpen_200() throws Exception {
        when(overdueAdminService.getCases(1L, "OPEN")).thenReturn(List.of());

        mockMvc.perform(get(LIST_URL).principal(token("1", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.requestId").exists());

        verify(overdueAdminService).getCases(eq(1L), eq("OPEN"));
    }

    @Test
    @DisplayName("목록: 회원 토큰이면 403, Service 호출 안 함")
    void list_member_403() throws Exception {
        mockMvc.perform(get(LIST_URL).principal(token("1", "ROLE_MEMBER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verify(overdueAdminService, never()).getCases(anyLong(), anyString());
    }

    @Test
    @DisplayName("목록: 로그인 정보가 없으면 401")
    void list_noLogin_401() throws Exception {
        mockMvc.perform(get(LIST_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verify(overdueAdminService, never()).getCases(anyLong(), anyString());
    }

    // ---------- 조치 POST ----------

    @Test
    @DisplayName("조치: 관리자 토큰이면 ADMIN_ID·경로의 미납 건 번호·본문을 Service에 넘김 → 200")
    void action_admin_200() throws Exception {
        mockMvc.perform(post(ACTION_URL).principal(token("1", "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(WARN_BODY))
                .andExpect(status().isOk());

        verify(overdueAdminService).applyAction(eq(1L), eq(10L), any(OverdueActionRequest.class));
    }

    @Test
    @DisplayName("조치: 회원 토큰이면 403, Service 호출 안 함")
    void action_member_403() throws Exception {
        mockMvc.perform(post(ACTION_URL).principal(token("1", "ROLE_MEMBER"))
                        .contentType(MediaType.APPLICATION_JSON).content(WARN_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verify(overdueAdminService, never()).applyAction(any(), any(), any());
    }

    @Test
    @DisplayName("조치: 본문 JSON이 깨져 있으면 400, Service 호출 안 함")
    void action_brokenJson_400() throws Exception {
        mockMvc.perform(post(ACTION_URL).principal(token("1", "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":"))
                .andExpect(status().isBadRequest());

        verify(overdueAdminService, never()).applyAction(any(), any(), any());
    }

    @Test
    @DisplayName("조치: 미납 건 번호가 숫자가 아니면 400, Service 호출 안 함")
    void action_badCaseId_400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/overdue-cases/abc/actions").principal(token("1", "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(WARN_BODY))
                .andExpect(status().isBadRequest());

        verify(overdueAdminService, never()).applyAction(any(), any(), any());
    }

    // JWT 필터가 만들어 주는 것과 같은 로그인 정보 (name = subject, 권한 1개)
    private static UsernamePasswordAuthenticationToken token(String id, String role) {
        return new UsernamePasswordAuthenticationToken(id, null, List.of(new SimpleGrantedAuthority(role)));
    }
}