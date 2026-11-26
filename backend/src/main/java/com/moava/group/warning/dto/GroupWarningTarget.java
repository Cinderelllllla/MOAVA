package com.moava.group.warning.dto;

/**
 * F30 경고·강제 탈퇴 판단 재료 (GYE_GROUP + 대상 회원의 GYE_MEMBER).
 *
 * @param groupTitle   모임 이름 (알림 문구의 {scope})
 * @param memberStatus 대상 회원의 GYE_MEMBER.STATUS. 이 모임 가입 기록이 없으면 null
 * @param warningCount 대상 회원의 누적 경고. 가입 기록이 없으면 null
 */
public record GroupWarningTarget(long groupId, String groupTitle, String groupStatus, long ownerId,
                                 String memberStatus, Integer warningCount) {

    public boolean isOwner(long memberId) {
        return ownerId == memberId;
    }

    /** 경고·강제 탈퇴 대상이 될 수 있는 회원: 이 모임의 ACTIVE 회원 */
    public boolean isActiveMember() {
        return "ACTIVE".equals(memberStatus);
    }

    public boolean dissolved() {
        return "DISSOLVED".equals(groupStatus);
    }
}