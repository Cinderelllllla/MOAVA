package com.moava.chat.dto;

/**
 * 한 회원이 한 모임 채팅방에서 무엇을 할 수 있는지 판단하는 재료 (ChatMapper.selectAccess 결과).
 *
 * @param groupStatus      GYE_GROUP.STATUS (모임이 없으면 null)
 * @param memberStatus     GYE_MEMBER.STATUS (가입 기록이 없으면 null)
 * @param chatRestrictedYn GYE_MEMBER.CHAT_RESTRICTED_YN — 미납 제재(F37)
 * @param lastChatSanction SANCTION 의 최신 CHAT_RESTRICT / CHAT_RELEASE (없으면 null) — 관리자 제재
 */
public record ChatAccess(String groupStatus, String memberStatus, String chatRestrictedYn, String lastChatSanction) {

    /** 지금 이 모임의 승인된(ACTIVE) 회원인가 → 읽기 가능 */
    public boolean canRead() {
        return groupStatus != null && "ACTIVE".equals(memberStatus);
    }

    public boolean dissolved() {
        return "DISSOLVED".equals(groupStatus);
    }

    /** 미납 제재 또는 관리자 채팅 제한 중인가 */
    public boolean restricted() {
        return "Y".equals(chatRestrictedYn) || "CHAT_RESTRICT".equals(lastChatSanction);
    }
}
