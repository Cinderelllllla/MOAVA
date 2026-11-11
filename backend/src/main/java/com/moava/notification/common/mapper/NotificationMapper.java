package com.moava.notification.common.mapper;

import com.moava.notification.common.dto.NotificationInsert;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NotificationMapper {

    /** 회원 1명. row.notificationId가 채워진다. */
    int insertForMember(NotificationInsert row);

    /** 모임 ACTIVE 회원 전원 (exclude 제외). 저장 건수 반환. */
    int insertForGroup(@Param("groupId") long groupId,
                       @Param("exclude") List<Long> exclude,
                       @Param("row") NotificationInsert row);

    /** ACTIVE 관리자 전원. 저장 건수 반환. */
    int insertForAdmins(@Param("row") NotificationInsert row);
}
