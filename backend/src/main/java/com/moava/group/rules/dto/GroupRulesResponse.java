package com.moava.group.rules.dto;

/**
 * F31 운영 규칙 조회·수정 응답 (GET·PUT 같은 모양).
 *
 * @param leader      로그인 회원이 이 모임의 모임장인지 (true 면 화면에 수정 폼을 보여 준다)
 * @param groupStatus GYE_GROUP.STATUS (RECRUITING / ACTIVE / DISSOLVE_PENDING / DISSOLVED)
 */
public record GroupRulesResponse(long groupId, String rulesTitle, String rulesBody, boolean leader,
                                 String groupStatus) {
}
