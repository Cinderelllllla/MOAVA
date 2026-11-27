package com.moava.chat.dto;

/**
 * 관리자 채팅 열람(MOA-96)에 필요한 신고 정보만. SUPPORT_REPORT 의 GROUP_ID·STATUS 만 읽고 신고 상세(DETAIL_ENC)는 읽지 않는다.
 *
 * @param groupId 대상 모임 (없으면 null)
 * @param status  RECEIVED / REVIEWING / MORE_INFO / CLOSED / REJECTED
 */
public record ChatReportRef(Long groupId, String status) {
}
