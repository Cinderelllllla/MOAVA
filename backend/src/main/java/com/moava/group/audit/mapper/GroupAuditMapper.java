package com.moava.group.audit.mapper;

import com.moava.group.audit.dto.GroupAuditAccessRow;
import com.moava.group.audit.dto.GroupAuditLogRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * F33 회원용 모임 운영 기록(AUDIT_LOG) 조회 전용 매퍼.
 * append-only 정책에 따라 UPDATE / DELETE 쿼리를 정의하지 않는다.
 */
@Mapper
public interface GroupAuditMapper {

    /** 모임 존재 여부 및 요청 회원의 ACTIVE 참여/모임장 여부 확인 */
    GroupAuditAccessRow selectAccess(@Param("groupId") long groupId,
                                     @Param("memberId") long memberId);

    /** 해당 모임의 회원 공개 대상 감사 로그 총 건수 */
    long countGroupAuditLogs(@Param("groupId") long groupId,
                             @Param("eventType") String eventType);

    /** 해당 모임의 회원 공개 대상 감사 로그 페이징 조회 (최신순) */
    List<GroupAuditLogRow> selectGroupAuditLogs(@Param("groupId") long groupId,
                                                @Param("eventType") String eventType,
                                                @Param("offset") int offset,
                                                @Param("size") int size);

    /** 해당 모임에 기록된 이벤트 유형 목록 (필터 드롭다운용, 비공개 이벤트 제외) */
    List<String> selectDistinctGroupEventTypes(@Param("groupId") long groupId);
}
