package com.moava.support.controller;

import com.moava.support.dto.SupportReportDTO;
import com.moava.support.service.SupportReportService;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F40 SupportReportController 단위 테스트.
 * - ADMIN 접수 시도 차단: 403 FORBIDDEN + '관리자는 신고 처리 메뉴를 이용해 주세요'
 * - MEMBER의 관리자 API 접근 차단: 403 FORBIDDEN
 * - 비로그인 접근 차단: 401 UNAUTHORIZED
 * - 회원 접수/내 목록/타인 열람 차단, 관리자 목록+필터/상태 변경(메모 필수) 검증
 */
@ExtendWith(MockitoExtension.class)
class SupportReportControllerTest {

    @Mock
    SupportReportService service;

    @InjectMocks
    SupportReportController controller;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new SupportReportExceptionHandler())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @Test
    @DisplayName("회원 신고 접수: ROLE_MEMBER 요청 시 200 OK + ApiResponse 반환")
    void createReport_member_200() throws Exception {
        when(service.createReport(eq(10L), any(SupportReportDTO.CreateReportRequest.class))).thenReturn(
                SupportReportDTO.CreateReportResponse.builder()
                        .report(SupportReportDTO.ReportItem.builder()
                                .reportId(701L)
                                .memberId(10L)
                                .reporterName("김○늘")
                                .groupId(101L)
                                .groupTitle("마포 저축 모임")
                                .category("ACCOUNT_CHANGE")
                                .categoryLabel("계좌 변경 의심")
                                .detail("상세 신고 내용")
                                .status("RECEIVED")
                                .statusLabel("접수")
                                .history(List.of())
                                .build())
                        .message("신고가 접수되었습니다.")
                        .guidanceNotice(SupportReportService.GUIDANCE_NOTICE)
                        .build()
        );

        mockMvc.perform(post("/api/v1/support/reports")
                        .principal(token("10", "ROLE_MEMBER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":101,\"category\":\"ACCOUNT_CHANGE\",\"detail\":\"상세 신고 내용\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.report.reportId").value(701))
                .andExpect(jsonPath("$.data.report.status").value("RECEIVED"))
                .andExpect(jsonPath("$.meta.requestId").exists());

        verify(service).createReport(eq(10L), any(SupportReportDTO.CreateReportRequest.class));
    }

    @Test
    @DisplayName("ADMIN 접수 시도 차단: 403 FORBIDDEN + '관리자는 신고 처리 메뉴를 이용해 주세요'")
    void createReport_admin_403WithSpecMessage() throws Exception {
        mockMvc.perform(post("/api/v1/support/reports")
                        .principal(token("1", "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":101,\"category\":\"ACCOUNT_CHANGE\",\"detail\":\"관리자 접수 시도\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("관리자는 신고 처리 메뉴를 이용해 주세요"));

        verify(service, never()).createReport(anyLong(), any());
    }

    @Test
    @DisplayName("비로그인 신고 접수 시도: 401 UNAUTHORIZED")
    void createReport_unauthenticated_401() throws Exception {
        mockMvc.perform(post("/api/v1/support/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"OTHER\",\"detail\":\"비로그인\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verify(service, never()).createReport(anyLong(), any());
    }

    @Test
    @DisplayName("회원 나의 신고 목록 조회: 200 OK")
    void getMyReports_member_200() throws Exception {
        when(service.getMyReports(10L)).thenReturn(
                SupportReportDTO.MemberReportListResponse.builder()
                        .items(List.of())
                        .totalCount(0)
                        .guidanceNotice(SupportReportService.GUIDANCE_NOTICE)
                        .build()
        );

        mockMvc.perform(get("/api/v1/support/reports").principal(token("10", "ROLE_MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0));

        verify(service).getMyReports(10L);
    }

    @Test
    @DisplayName("회원 타인 신고 단건 열람 시도: 403 FORBIDDEN")
    void getReportDetail_otherMember_403() throws Exception {
        when(service.getMyReportDetail(20L, 701L))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "본인이 접수한 신고만 열람할 수 있습니다."));

        mockMvc.perform(get("/api/v1/support/reports/701").principal(token("20", "ROLE_MEMBER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("관리자 전체 신고 목록 조회 + 상태 필터: ROLE_ADMIN 200 OK")
    void getAdminReports_admin_200() throws Exception {
        when(service.getAdminReports(1L, "RECEIVED")).thenReturn(
                SupportReportDTO.AdminReportListResponse.builder()
                        .items(List.of())
                        .totalCount(0)
                        .statusFilter("RECEIVED")
                        .statusCounts(Map.of("ALL", 0, "RECEIVED", 0))
                        .guidanceNotice(SupportReportService.GUIDANCE_NOTICE)
                        .build()
        );

        mockMvc.perform(get("/api/v1/admin/support/reports")
                        .param("status", "RECEIVED")
                        .principal(token("1", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusFilter").value("RECEIVED"));

        verify(service).getAdminReports(1L, "RECEIVED");
    }

    @Test
    @DisplayName("관리자 신고 목록 조회: 일반 회원 접근 시 403 FORBIDDEN")
    void getAdminReports_member_403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/support/reports").principal(token("10", "ROLE_MEMBER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verify(service, never()).getAdminReports(anyLong(), any());
    }

    @Test
    @DisplayName("관리자 상태 변경 성공: 200 OK")
    void updateReportStatus_admin_200() throws Exception {
        when(service.updateReportStatus(eq(1L), eq(701L), any(SupportReportDTO.StatusUpdateRequest.class)))
                .thenReturn(SupportReportDTO.StatusUpdateResponse.builder()
                        .previousStatus("RECEIVED")
                        .previousStatusLabel("접수")
                        .currentStatus("REVIEWING")
                        .currentStatusLabel("검토 중")
                        .message("신고 처리 상태를 저장했습니다.")
                        .build());

        mockMvc.perform(put("/api/v1/admin/support/reports/701/status")
                        .principal(token("1", "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWING\",\"adminMemo\":\"사실 관계 확인 중\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStatus").value("REVIEWING"))
                .andExpect(jsonPath("$.data.currentStatusLabel").value("검토 중"));
    }

    @Test
    @DisplayName("관리자 상태 변경: 메모 누락 시 400 BAD_REQUEST + '처리 메모가 필요합니다'")
    void updateReportStatus_missingMemo_400WithSpecMessage() throws Exception {
        when(service.updateReportStatus(eq(1L), eq(701L), any(SupportReportDTO.StatusUpdateRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "처리 메모가 필요합니다"));

        mockMvc.perform(put("/api/v1/admin/support/reports/701/status")
                        .principal(token("1", "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\",\"adminMemo\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("처리 메모가 필요합니다"));
    }

    private static UsernamePasswordAuthenticationToken token(String principal, String role) {
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority(role))
        );
    }
}
