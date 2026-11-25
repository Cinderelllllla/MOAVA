package com.moava.admin.sanctions.controller;

import com.moava.admin.sanctions.dto.AdminSanctionDTO;
import com.moava.admin.sanctions.service.AdminSanctionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F39 AdminSanctionController 단위 테스트.
 * - 권한 검증(ROLE_ADMIN만 허용, 회원 403, 비로그인 401)
 * - 가입 제한 설정/해제, 경고 현황, 이력 검색·유형 필터·8건 페이징 전달 검증
 */
@ExtendWith(MockitoExtension.class)
class AdminSanctionControllerTest {

    @Mock
    AdminSanctionService service;

    @InjectMocks
    AdminSanctionController controller;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AdminSanctionExceptionHandler())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @Test
    @DisplayName("종합 조회: 관리자 토큰이면 200 + ApiResponse 반환")
    void getOverview_admin_200() throws Exception {
        when(service.getOverview(1L)).thenReturn(
                AdminSanctionDTO.SanctionOverviewResponse.builder()
                        .candidates(List.of())
                        .bannedMembers(List.of())
                        .bannedCount(0)
                        .warnings(List.of())
                        .build()
        );

        mockMvc.perform(get("/api/v1/admin/sanctions/overview").principal(token("1", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bannedCount").value(0))
                .andExpect(jsonPath("$.meta.requestId").exists());

        verify(service).getOverview(1L);
    }

    @Test
    @DisplayName("종합 조회: 일반 회원 토큰이면 403 FORBIDDEN")
    void getOverview_member_403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/sanctions/overview").principal(token("1", "ROLE_MEMBER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verify(service, never()).getOverview(anyLong());
    }

    @Test
    @DisplayName("종합 조회: 비로그인이면 401 UNAUTHORIZED")
    void getOverview_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/sanctions/overview"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verify(service, never()).getOverview(anyLong());
    }

    @Test
    @DisplayName("가입 제한 설정: 관리자 요청 시 200 반환")
    void banMember_admin_200() throws Exception {
        when(service.banMember(eq(1L), any(AdminSanctionDTO.BanRequest.class))).thenReturn(
                AdminSanctionDTO.BanActionResponse.builder()
                        .sanctionId(501L)
                        .memberId(10L)
                        .memberName("김○늘")
                        .sanctionType("BAN")
                        .banned(true)
                        .reason("허위 증빙 반복 제출")
                        .issuedAdminId(1L)
                        .message("플랫폼 가입 제한을 설정했어요.")
                        .build()
        );

        mockMvc.perform(post("/api/v1/admin/sanctions/bans")
                        .principal(token("1", "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":10,\"reason\":\"허위 증빙 반복 제출\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sanctionType").value("BAN"))
                .andExpect(jsonPath("$.data.banned").value(true));
    }

    @Test
    @DisplayName("가입 제한 설정: 중복 제재 409 CONFLICT 시 명세 메시지 반환")
    void banMember_duplicate_409() throws Exception {
        when(service.banMember(eq(1L), any(AdminSanctionDTO.BanRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입 제한 중인 회원입니다."));

        mockMvc.perform(post("/api/v1/admin/sanctions/bans")
                        .principal(token("1", "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":10,\"reason\":\"중복 제재\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"))
                .andExpect(jsonPath("$.error.message").value("이미 가입 제한 중인 회원입니다."));
    }

    @Test
    @DisplayName("가입 제한 해제: 관리자 요청 시 200 반환")
    void releaseBan_admin_200() throws Exception {
        when(service.releaseBan(eq(1L), eq(10L), any())).thenReturn(
                AdminSanctionDTO.BanActionResponse.builder()
                        .sanctionId(502L)
                        .memberId(10L)
                        .memberName("김○늘")
                        .sanctionType("BAN_RELEASE")
                        .banned(false)
                        .reason("관리자 플랫폼 가입 제한 해제")
                        .issuedAdminId(1L)
                        .message("플랫폼 가입 제한을 해제했어요.")
                        .build()
        );

        mockMvc.perform(post("/api/v1/admin/sanctions/bans/10/release")
                        .principal(token("1", "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sanctionType").value("BAN_RELEASE"))
                .andExpect(jsonPath("$.data.banned").value(false));
    }

    @Test
    @DisplayName("경고·강제 탈퇴 이력: 파라미터 생략 시 기본값(keyword=null, type=ALL, page=0, size=8) 전달")
    void getHistory_defaults_200() throws Exception {
        when(service.getWarningExpelHistory(1L, null, "ALL", 0, 8)).thenReturn(
                AdminSanctionDTO.SanctionHistoryPageResponse.builder()
                        .items(List.of())
                        .page(0)
                        .size(8)
                        .totalCount(0)
                        .totalPages(0)
                        .hasPrev(false)
                        .hasNext(false)
                        .build()
        );

        mockMvc.perform(get("/api/v1/admin/sanctions/history").principal(token("1", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(8));

        verify(service).getWarningExpelHistory(1L, null, "ALL", 0, 8);
    }

    private static UsernamePasswordAuthenticationToken token(String principal, String role) {
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority(role))
        );
    }
}
