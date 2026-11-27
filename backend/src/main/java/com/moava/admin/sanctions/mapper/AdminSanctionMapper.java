package com.moava.admin.sanctions.mapper;

import com.moava.admin.sanctions.dto.AdminSanctionDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * F39 (A03) 플랫폼 가입 제한 설정·해제 및 경고 현황 매퍼.
 */
@Mapper
public interface AdminSanctionMapper {

    int countActiveAdmin(@Param("adminId") Long adminId);

    List<AdminSanctionDTO.BanCandidateRow> selectBanCandidates();

    List<AdminSanctionDTO.BannedMemberRow> selectBannedMembers();

    AdminSanctionDTO.MemberBanTargetRow selectMemberForBanUpdate(@Param("memberId") Long memberId);

    int updateMemberBannedYn(
            @Param("memberId") Long memberId,
            @Param("fromYn") String fromYn,
            @Param("toYn") String toYn
    );

    Long nextSanctionId();

    int insertAdminSanction(
            @Param("sanctionId") Long sanctionId,
            @Param("sanctionType") String sanctionType,
            @Param("memberId") Long memberId,
            @Param("adminId") Long adminId,
            @Param("reason") String reason
    );

    int insertAdminAuditLog(
            @Param("adminId") Long adminId,
            @Param("targetMemberId") Long targetMemberId,
            @Param("eventType") String eventType,
            @Param("description") String description
    );

    List<AdminSanctionDTO.MemberWarningRow> selectMemberWarnings();

    long countWarningExpelHistory(AdminSanctionDTO.SanctionHistorySearchCriteria criteria);

    List<AdminSanctionDTO.SanctionHistoryRow> selectWarningExpelHistory(
            AdminSanctionDTO.SanctionHistorySearchCriteria criteria
    );
}
