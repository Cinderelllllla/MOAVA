package com.moava.group.create.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface GroupJoinCodeIssueMapper {

    // 모임 정보 및 모임장 권한 확인용 조회
    Map<String, Object> findGroupForIssue(
            @Param("groupId") Long groupId
    );

    // 최초 발급 또는 재발급
    int updateJoinCode(
            @Param("groupId") Long groupId,
            @Param("ownerId") Long ownerId,
            @Param("encryptedCode") String encryptedCode,
            @Param("keyVersion") int keyVersion
    );
}
