package com.moava.chat.dto;

import java.util.List;

/**
 * 이전 메시지 한 묶음 (오래된 것 → 최신 순).
 *
 * @param hasMore 더 오래된 메시지가 남아 있으면 true → 다음 요청에 beforeId = items[0].messageId
 * @param canSend 지금 이 방에서 보내기 가능 여부
 */
public record ChatHistoryResponse(List<ChatMessageResponse> items, boolean hasMore, boolean canSend) {
}
