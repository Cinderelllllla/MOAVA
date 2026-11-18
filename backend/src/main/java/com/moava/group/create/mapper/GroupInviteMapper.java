package com.moava.group.create.mapper;

import com.moava.group.create.dto.GroupInviteResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GroupInviteMapper {

    GroupInviteResponse findByInviteCode(
            @Param("inviteCode") String inviteCode
    );
}