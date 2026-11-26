package com.moava.group.rules.mapper;

import com.moava.group.rules.dto.GroupRulesRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * F31 운영 규칙 전용 쿼리. 다른 팀원 Mapper(create·procedures)는 고치지 않고 여기서 새로 쓴다.
 */
@Mapper
public interface GroupRulesMapper {

    /** 모임 규칙 + 로그인 회원의 가입 상태. 모임이 없으면 null. */
    GroupRulesRow selectRules(@Param("groupId") long groupId, @Param("memberId") long memberId);

    int updateRules(@Param("groupId") long groupId, @Param("rulesTitle") String rulesTitle,
                    @Param("rulesBody") String rulesBody);

    /** AUDIT_LOG 회원 행위 기록 (ACTOR_ADMIN_ID 는 NULL) */
    int insertAuditLog(@Param("groupId") long groupId, @Param("actorMemberId") long actorMemberId,
                       @Param("eventType") String eventType, @Param("description") String description);
}
