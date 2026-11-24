package com.moava.overdue.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * F26 관리자 조치 요청 — {"action": "WARN", "reason": "..."}
 */
@Getter
@Setter
public class OverdueActionRequest {
    private String action;      // WARN(경고) / CHAT_RESTRICT(채팅 제한) / CHAT_RELEASE(해제) / ORDER_REVIEW(후순위 검토)
    private String reason;      // 제한·해제·후순위는 필수(SANCTION.REASON), 경고는 선택
}