package com.moava.overdue.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * F26 미납 대상 1건 = (모임, 회원, 귀속 월). 감지 쿼리 결과 + OVERDUE_CASE 저장 후 채워지는 ID
 */
@Getter
@Setter
public class OverdueTargetDto {
    private Long overdueCaseId;     // insertOverdueCase 후 채워짐 (selectKey)
    private Long groupId;
    private String groupTitle;
    private Long memberId;
    private String memberName;      // 관리자 알림용 (보낼 때 마스킹)
    private String payMonth;        // YYYYMM
    private LocalDate dueDate;      // 납부 기한 = 해당 월 PAY_DAY
    private Integer elapsedDays;    // 기준일 - 납부 기한
}