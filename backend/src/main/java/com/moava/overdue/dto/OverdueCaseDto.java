package com.moava.overdue.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * F26 관리자 미납 목록(A06) 1행 — 모임·회원·귀속 월·경과일·상태 (화면목록 A06)
 */
@Getter
@Setter
public class OverdueCaseDto {
    private Long overdueCaseId;
    private Long groupId;
    private String groupTitle;
    private Long memberId;
    private String memberName;          // Service에서 마스킹 (F24 관리자 목록과 같은 기준)
    private String payMonth;            // YYYYMM
    private LocalDate dueDate;          // 납부 기한
    private Integer elapsedDays;        // 오늘 - 납부 기한 (DB에 저장하지 않고 조회 시 계산, DDL v1.3)
    private String status;              // DETECTED / WARNED / RESTRICTED / RESOLVED
    private String overduePolicy;       // ALERT_ONLY면 채팅 제한 조치 비활성 (명세 F26-3)
    private LocalDateTime warnedAt;
    private LocalDateTime resolvedAt;
    private String chatRestrictedYn;    // 회원 채팅 제한 여부 Y/N — A06에서 '제한 해제' 버튼 표시용
}