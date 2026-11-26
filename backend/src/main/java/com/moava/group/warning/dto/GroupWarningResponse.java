package com.moava.group.warning.dto;

/**
 * 경고·강제 탈퇴 처리 결과.
 * @param memberStatus ACTIVE(경고 후) / EXPELLED(강제 탈퇴 후)
 * @param expelReviewable 경고 2회 도달 → 화면에 '강제 탈퇴 검토 대상' 표시용 (자동 탈퇴 아님)
 */
public record GroupWarningResponse(long groupId, long memberId, int warningCount,
                                   String memberStatus, boolean expelReviewable) {
}