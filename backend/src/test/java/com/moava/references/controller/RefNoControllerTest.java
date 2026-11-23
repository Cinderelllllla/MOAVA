package com.moava.references.controller;

import com.moava.proofs.controller.ProofExceptionHandler;
import com.moava.references.service.RefNoService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RefNoController 단위 테스트 (F24) — 관리자 토큰에서 ADMIN_ID를 꺼내는지, 회원 토큰은 막는지 확인
 */
@ExtendWith(MockitoExtension.class)
class RefNoControllerTest {

    @Mock
    RefNoService refNoService;                  // 가짜 Service

    @InjectMocks
    RefNoController refNoController;            // 진짜 Controller

    MockMvc mockMvc;

    static final String LIST_URL = "/api/v1/admin/payment-proofs";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(refNoController)
                .setControllerAdvice(new ProofExceptionHandler())             // 오류 응답 형식
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)   // 한글 메시지가 깨지지 않게
                .build();
    }

    @Test
    @DisplayName("관리자 토큰이면 subject(ADMIN_ID)를 꺼내 Service에 넘김 → 200")
    void admin_passesAdminId() throws Exception {
        when(refNoService.getProofs(1L, "UNASSIGNED")).thenReturn(List.of());

        mockMvc.perform(get(LIST_URL).principal(token("1", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.requestId").exists());

        verify(refNoService).getProofs(eq(1L), eq("UNASSIGNED"));
    }

    @Test
    @DisplayName("회원 토큰이면 같은 번호라도 403, Service 호출 안 함 (회원 1번 ≠ 관리자 1번)")
    void member_403() throws Exception {
        mockMvc.perform(get(LIST_URL).principal(token("1", "ROLE_MEMBER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("관리자만 이용할 수 있습니다."));

        verify(refNoService, never()).getProofs(any(), any());
    }

    @Test
    @DisplayName("로그인 정보가 없으면 401")
    void noLogin_401() throws Exception {
        mockMvc.perform(get(LIST_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verify(refNoService, never()).getProofs(any(), any());
    }

    // JWT 필터가 만들어 주는 것과 같은 로그인 정보 (name = subject, 권한 1개)
    private static UsernamePasswordAuthenticationToken token(String id, String role) {
        return new UsernamePasswordAuthenticationToken(id, null, List.of(new SimpleGrantedAuthority(role)));
    }
}