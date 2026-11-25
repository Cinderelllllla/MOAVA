package com.moava.finance.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** AI 수령 순서 추천용 읽기 전용 조회 (GYE_GROUP, GYE_MEMBER). 쓰기는 하지 않는다. */
@Mapper
public interface AiPayoutMapper {

    /** 모임장 회원 번호. 모임이 없으면 null. */
    Long selectOwnerId(@Param("groupId") long groupId);

    /** 모임의 승인된(ACTIVE) 회원 번호, 가입 순. */
    List<Long> selectActiveMemberIds(@Param("groupId") long groupId);
}
