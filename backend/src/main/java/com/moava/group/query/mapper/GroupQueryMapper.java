package com.moava.group.query.mapper;

import com.moava.group.query.dto.GroupListResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GroupQueryMapper {

    List<GroupListResponse> findMyOwnedGroups(
            @Param("memberId") Long memberId
    );
}