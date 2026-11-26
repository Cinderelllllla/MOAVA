package com.moava.group.rules.dto;

/** PUT /api/v1/groups/{groupId}/rules 요청 본문 */
public record GroupRulesUpdateRequest(String rulesTitle, String rulesBody) {
}
