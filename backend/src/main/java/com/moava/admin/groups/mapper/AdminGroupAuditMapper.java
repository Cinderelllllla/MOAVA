package com.moava.admin.groups.mapper;

import com.moava.admin.groups.dto.AdminGroupAuditDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * F38 (A02) 관리자 계모임 관리 및 감사 이력 조회 매퍼 (조회 전용).
 */
@Mapper
public interface AdminGroupAuditMapper {

    int countActiveAdmin(@Param("adminId") Long adminId);

    List<AdminGroupAuditDTO.GroupSummaryRow> selectAdminGroupList();

    AdminGroupAuditDTO.GroupDetailRow selectAdminGroupDetail(@Param("groupId") Long groupId);

    AdminGroupAuditDTO.GroupAccountRow selectGroupAccountByStatus(
            @Param("groupId") Long groupId,
            @Param("accountStatus") String accountStatus
    );

    long countAuditLogs(AdminGroupAuditDTO.AuditLogSearchCriteria criteria);

    List<AdminGroupAuditDTO.AuditLogRow> selectAuditLogs(AdminGroupAuditDTO.AuditLogSearchCriteria criteria);

    List<String> selectDistinctAuditEventTypes();

    List<AdminGroupAuditDTO.AdminOption> selectAdminOptions();
}
