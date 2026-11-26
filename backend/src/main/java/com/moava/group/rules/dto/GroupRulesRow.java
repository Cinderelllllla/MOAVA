package com.moava.group.rules.dto;

/**
 * 규칙 조회·권한 판단 재료 (GYE_GROUP + 로그인 회원의 GYE_MEMBER 상태).
 *
 * @param groupTitle   모임 이름 (알림 문구의 {group})
 * @param memberStatus 로그인 회원의 GYE_MEMBER.STATUS. 가입 기록이 없으면 null
 */
public record GroupRulesRow(long groupId, String groupTitle, String groupStatus, long ownerId,
                            String rulesTitle, String rulesBody, String memberStatus) {

    public boolean isOwner(long memberId) {
        return ownerId == memberId;
    }

    /** 규칙을 볼 수 있는 사람: 이 모임의 ACTIVE 회원 (모임장 포함) */
    public boolean canRead(long memberId) {
        return isOwner(memberId) || "ACTIVE".equals(memberStatus);
    }

    public boolean dissolved() {
        return "DISSOLVED".equals(groupStatus);
    }
}
