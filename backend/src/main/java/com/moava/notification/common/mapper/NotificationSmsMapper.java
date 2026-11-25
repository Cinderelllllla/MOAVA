package com.moava.notification.common.mapper;

import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 문자 발송용: 받는 회원의 암호화 번호 조회, 발송 성공 기록 */
@Mapper
public interface NotificationSmsMapper {

    /** PHONE_ENC, KEY_VER, STATUS. 회원이 없으면 null */
    Map<String, Object> findSmsTarget(@Param("memberId") long memberId);

    /** 발송 성공 시 SMS_SENT_YN='Y'. 반환: 갱신 건수 */
    int markSmsSent(@Param("notificationId") long notificationId);
}
