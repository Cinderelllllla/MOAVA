package com.moava.admin.dashboard.service;

import com.moava.admin.dashboard.dto.AdminDashboardDTO;
import com.moava.admin.dashboard.mapper.AdminDashboardMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock
    private AdminDashboardMapper dashboardMapper;

    @Mock
    private Environment environment;

    private AdminDashboardService service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardService(dashboardMapper, environment);
    }

    @Test
    @DisplayName("대시보드 조회: 4대 지표, 행위자 라벨(관리자·회원 마스킹·시스템), non-prod 시 demoEnabled=true")
    void getDashboardSummary_returnsStatsMaskedActorsAndDemoEnabled() {
        when(dashboardMapper.countActiveAdmin(1L)).thenReturn(1);

        AdminDashboardDTO.SummaryStats stats = new AdminDashboardDTO.SummaryStats();
        stats.setTotalMembers(15);
        stats.setTotalGroups(3);
        stats.setPendingDissolutions(1);
        stats.setBannedMembers(2);
        when(dashboardMapper.selectDashboardStats()).thenReturn(stats);

        AdminDashboardDTO.RecentAuditRow adminRow = new AdminDashboardDTO.RecentAuditRow();
        adminRow.setAuditLogId(1L);
        adminRow.setGroupTitle("플랫폼 공통");
        adminRow.setEventType("BAN");
        adminRow.setActorAdminId(1L);
        adminRow.setAdminName("홍운영");
        adminRow.setDescription("가입 제한");
        adminRow.setCreatedAt("2026-11-25 10:00");

        AdminDashboardDTO.RecentAuditRow memberRow = new AdminDashboardDTO.RecentAuditRow();
        memberRow.setAuditLogId(2L);
        memberRow.setGroupId(10L);
        memberRow.setGroupTitle("마포 목돈 모임");
        memberRow.setEventType("GROUP_CREATE");
        memberRow.setActorMemberId(5L);
        memberRow.setMemberName("김하늘");
        memberRow.setDescription("모임 개설");
        memberRow.setCreatedAt("2026-11-25 09:00");

        AdminDashboardDTO.RecentAuditRow systemRow = new AdminDashboardDTO.RecentAuditRow();
        systemRow.setAuditLogId(3L);
        systemRow.setGroupId(10L);
        systemRow.setGroupTitle("마포 목돈 모임");
        systemRow.setEventType("OVERDUE_DETECT");
        systemRow.setDescription("미납 5일차 자동 감지");
        systemRow.setCreatedAt("2026-11-25 02:00");

        when(dashboardMapper.selectRecentAuditLogs()).thenReturn(List.of(adminRow, memberRow, systemRow));
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});

        AdminDashboardDTO.DashboardResponse result = service.getDashboardSummary(1L);

        assertThat(result.getStats().getTotalMembers()).isEqualTo(15);
        assertThat(result.isDemoEnabled()).isTrue();
        assertThat(result.getRecentLogs()).hasSize(3);
        assertThat(result.getRecentLogs().get(0).getActorLabel()).isEqualTo("관리자 홍운영");
        assertThat(result.getRecentLogs().get(1).getActorLabel()).isEqualTo("김○늘");
        assertThat(result.getRecentLogs().get(2).getActorLabel()).isEqualTo("시스템");
    }

    @Test
    @DisplayName("대시보드 조회: prod 프로필 활성 시 demoEnabled=false")
    void getDashboardSummary_disablesDemoOnProdProfile() {
        when(dashboardMapper.countActiveAdmin(1L)).thenReturn(1);
        when(dashboardMapper.selectDashboardStats()).thenReturn(new AdminDashboardDTO.SummaryStats());
        when(dashboardMapper.selectRecentAuditLogs()).thenReturn(List.of());
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        AdminDashboardDTO.DashboardResponse result = service.getDashboardSummary(1L);

        assertThat(result.isDemoEnabled()).isFalse();
    }

    @Test
    @DisplayName("대시보드 조회: 비활성 관리자 계정은 403 차단")
    void getDashboardSummary_rejectsInactiveAdmin() {
        when(dashboardMapper.countActiveAdmin(99L)).thenReturn(0);

        assertThatThrownBy(() -> service.getDashboardSummary(99L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
