package com.moava.notification.common.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.moava.notification.common.dto.NotificationItem;
import com.moava.notification.common.dto.NotificationReceiver;

/**
 * 알림함 조회·읽음·숨김. 모든 쿼리는 받는 사람(r) 조건이 붙어서 남의 알림은 건드릴 수 없다.
 */
@Mapper
public interface NotificationInboxMapper {

    /** read: null이면 전체, "Y" 읽음만, "N" 안 읽음만. 숨긴 알림은 제외. */
    List<NotificationItem> selectList(@Param("r") NotificationReceiver receiver,
                                      @Param("read") String read,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    int countUnread(@Param("r") NotificationReceiver receiver);

    int markRead(@Param("r") NotificationReceiver receiver, @Param("id") long notificationId);

    int markAllRead(@Param("r") NotificationReceiver receiver);

    int hide(@Param("r") NotificationReceiver receiver, @Param("ids") List<Long> ids);
}
