package com.moava.group.warning.mapper;

import com.moava.group.warning.dto.GroupWarningTarget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * F30 모임 경고·강제 탈퇴 전용 쿼리. 다른 팀원 Mapper(create·procedures·rules)는 고치지 않고 여기서 새로 쓴다.
 */
@Mapper
public interface GroupWarningMapper {

    /** 모임 정보 + 대상 회원 상태·경고 횟수. 모임이 없으면 null */
    GroupWarningTarget selectTarget(@Param("groupId") long groupId, @Param("targetMemberId") long targetMemberId);

    /** 경고 +1. ACTIVE이고 2회 미만일 때만 바뀜 → 반환값 0이면 상한 초과 또는 상태 변경 */
    int increaseWarning(@Param("groupId") long groupId, @Param("targetMemberId") long targetMemberId);

    /** 강제 탈퇴. ACTIVE이고 경고 2회일 때만 바뀜 → 반환값 0이면 조건 미충족 */
    int expel(@Param("groupId") long groupId, @Param("targetMemberId") long targetMemberId);

    /** 모임 행 잠금. F29 중도 탈퇴 신청(requestWithdrawal)과 같은 GYE_GROUP 행을 잠가 동시 요청을 순서대로 처리 */
    Long lockGroup(@Param("groupId") long groupId);

    /** 대상 회원의 진행 중 중도 탈퇴 신청 수 (SETTLEMENT_PENDING·CONSENT_PENDING) */
    int countActiveWithdrawal(@Param("groupId") long groupId, @Param("targetMemberId") long targetMemberId);

    /** SANCTION 기록 (모임장 조치라 ISSUED_ADMIN_ID 는 NULL) */
    int insertSanction(@Param("sanctionType") String sanctionType, @Param("targetMemberId") long targetMemberId,
                       @Param("groupId") long groupId, @Param("issuedMemberId") long issuedMemberId,
                       @Param("reason") String reason);

    /** AUDIT_LOG 모임장 행위 기록 (ACTOR_ADMIN_ID 는 NULL) */
    int insertAuditLog(@Param("groupId") long groupId, @Param("actorMemberId") long actorMemberId,
                       @Param("targetMemberId") long targetMemberId, @Param("eventType") String eventType,
                       @Param("description") String description);
}