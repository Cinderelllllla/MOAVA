package com.moava.support.mapper;

import com.moava.support.dto.SupportReportDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * F40 (S15 · A08) 고객센터 신고 접수 및 처리 매퍼.
 */
@Mapper
public interface SupportReportMapper {

    int countActiveMember(@Param("memberId") Long memberId);

    int countActiveAdmin(@Param("adminId") Long adminId);

    List<SupportReportDTO.MemberGroupOptionRow> selectMemberGroups(@Param("memberId") Long memberId);

    SupportReportDTO.GroupBasicRow selectGroupById(@Param("groupId") Long groupId);

    Long nextSupportReportId();

    int insertSupportReport(
            @Param("reportId") Long reportId,
            @Param("memberId") Long memberId,
            @Param("groupId") Long groupId,
            @Param("category") String category,
            @Param("detailEnc") String detailEnc,
            @Param("keyVer") int keyVer
    );

    SupportReportDTO.SupportReportRow selectReportById(@Param("reportId") Long reportId);

    SupportReportDTO.SupportReportRow selectReportForUpdate(@Param("reportId") Long reportId);

    List<SupportReportDTO.SupportReportRow> selectReportsByMemberId(@Param("memberId") Long memberId);

    List<SupportReportDTO.SupportReportRow> selectAdminReports(@Param("status") String status);

    List<SupportReportDTO.StatusCountRow> selectStatusCounts();

    int updateReportStatus(
            @Param("reportId") Long reportId,
            @Param("status") String status,
            @Param("adminMemo") String adminMemo,
            @Param("handledBy") Long handledBy
    );

    int insertMemberAuditLog(
            @Param("groupId") Long groupId,
            @Param("actorMemberId") Long actorMemberId,
            @Param("eventType") String eventType,
            @Param("description") String description
    );

    int insertAdminAuditLog(
            @Param("groupId") Long groupId,
            @Param("actorAdminId") Long actorAdminId,
            @Param("targetMemberId") Long targetMemberId,
            @Param("eventType") String eventType,
            @Param("description") String description
    );

    List<SupportReportDTO.StatusHistoryRow> selectReportStatusHistoryByReportId(@Param("reportId") Long reportId);

    List<SupportReportDTO.StatusHistoryRow> selectReportStatusHistoryByMemberId(@Param("memberId") Long memberId);

    List<SupportReportDTO.StatusHistoryRow> selectAdminReportStatusHistory(@Param("status") String status);
}
