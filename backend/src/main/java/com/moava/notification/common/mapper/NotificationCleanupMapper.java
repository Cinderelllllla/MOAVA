package com.moava.notification.common.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 오래된 알림 정리 배치용. 실제 DELETE가 아니라 DELETED_YN='Y'(숨김)로 바꾼다.
 * 회원·관리자 알림 모두 대상. 이미 숨긴 알림은 건드리지 않는다.
 */
@Mapper
public interface NotificationCleanupMapper {

    /** 읽은 알림 중 만든 지 days일이 지난 것 숨김. 반환: 숨긴 건수 */
    int hideOldRead(@Param("days") int days);

    /** 안 읽은 알림 중 만든 지 days일이 지난 것 숨김. 반환: 숨긴 건수 */
    int hideOldUnread(@Param("days") int days);
}
