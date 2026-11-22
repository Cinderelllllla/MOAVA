package com.moava.group.create.mapper;

import com.moava.group.create.dto.GroupInviteResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GroupInviteMapper {

    GroupInviteResponse findByInviteCode(
            @Param("inviteCode") String inviteCode
    );

    // [F18 추가] 모임장 권한 확인 후 초대 토큰 조회
    String findInviteCodeForOwner(
            @Param("groupId") Long groupId,
            @Param("ownerId") Long ownerId
    );
}