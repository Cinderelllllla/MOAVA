package com.moava.group.warning.dto;

/** POST .../warnings, POST .../expulsion 요청 본문 */
public record GroupWarningRequest(String reason) {
}