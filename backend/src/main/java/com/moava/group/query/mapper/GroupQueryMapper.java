
package com.moava.group.query.mapper;

import com.moava.group.query.dto.GroupListResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GroupQueryMapper {

    // 기존: 내가 모임장인 모임 조회
    List<GroupListResponse> findMyOwnedGroups(
            @Param("memberId") Long memberId
    );

    // 추가: 내가 일반 회원으로 참여 중인 모임 조회
    List<GroupListResponse> findMyJoinedGroups(
            @Param("memberId") Long memberId
    );
}
