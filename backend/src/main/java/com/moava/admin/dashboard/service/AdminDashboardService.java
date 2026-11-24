package com.moava.admin.dashboard.service;

import com.moava.admin.dashboard.dto.AdminDashboardDTO;
import com.moava.admin.dashboard.mapper.AdminDashboardMapper;
import com.moava.common.util.NameMasker;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;

@Service
public class AdminDashboardService {

    private final AdminDashboardMapper dashboardMapper;
    private final Environment environment;

    public AdminDashboardService(AdminDashboardMapper dashboardMapper, Environment environment) {
        this.dashboardMapper = dashboardMapper;
        this.environment = environment;
    }

    /**
     * F37: 4대 통계 지표 + 최근 운영 이력 7건 + 운영 프로필 여부(demoEnabled) 조회
     */
    @Transactional(readOnly = true)
    public AdminDashboardDTO.DashboardResponse getDashboardSummary(Long adminId) {
        requireActiveAdmin(adminId);

        AdminDashboardDTO.SummaryStats stats = dashboardMapper.selectDashboardStats();
        List<AdminDashboardDTO.RecentAuditItem> recentLogs = dashboardMapper.selectRecentAuditLogs()
                .stream()
                .map(this::toRecentAuditItem)
                .toList();

        boolean isProd = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile));

        return AdminDashboardDTO.DashboardResponse.builder()
                .stats(stats)
                .recentLogs(recentLogs)
                .demoEnabled(!isProd)
                .build();
    }

    private void requireActiveAdmin(Long adminId) {
        if (adminId == null || dashboardMapper.countActiveAdmin(adminId) == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "활성화된 플랫폼 관리자 계정만 조회할 수 있습니다.");
        }
    }

    private AdminDashboardDTO.RecentAuditItem toRecentAuditItem(AdminDashboardDTO.RecentAuditRow row) {
        return AdminDashboardDTO.RecentAuditItem.builder()
                .auditLogId(row.getAuditLogId())
                .groupId(row.getGroupId())
                .groupTitle(row.getGroupTitle())
                .eventType(row.getEventType())
                .actorLabel(formatActorLabel(row))
                .description(row.getDescription())
                .createdAt(row.getCreatedAt())
                .build();
    }

    private String formatActorLabel(AdminDashboardDTO.RecentAuditRow row) {
        if (row.getActorAdminId() != null) {
            String adminName = row.getAdminName() == null ? "" : row.getAdminName().trim();
            return adminName.isEmpty() ? "관리자" : "관리자 " + adminName;
        }
        if (row.getActorMemberId() != null) {
            String memberName = row.getMemberName() == null ? null : row.getMemberName().trim();
            return NameMasker.mask(memberName);
        }
        return "시스템";
    }
}