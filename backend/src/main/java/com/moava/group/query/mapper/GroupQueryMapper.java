
package com.moava.group.query.mapper;

import com.moava.group.query.dto.GroupListResponse;
import com.moava.group.query.dto.GroupMemberResponse;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GroupQueryMapper {

    // 내가 모임장인 모임 조회
    List<GroupListResponse> findMyOwnedGroups(
            @Param("memberId") Long memberId
    );

    // 내가 일반 회원으로 참여 중인 모임 조회
    List<GroupListResponse> findMyJoinedGroups(
            @Param("memberId") Long memberId
    );

    // 해당 모임의 모임장 여부 확인
    int countOwnedGroup(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId
    );

    // 해당 모임의 ACTIVE 참여 회원 목록 조회
    List<GroupMemberResponse> findActiveGroupMembers(
            @Param("groupId") Long groupId
    );
}
