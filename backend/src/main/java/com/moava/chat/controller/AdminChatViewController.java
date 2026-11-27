package com.moava.chat.controller;

import com.moava.chat.dto.ChatHistoryResponse;
import com.moava.chat.service.ChatException;
import com.moava.chat.service.ChatService;
import com.moava.common.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * F34 관리자 신고 건 대상 모임 채팅 열람 (MOA-96) — 읽기 전용 REST.
 * <pre>
 * GET /api/v1/admin/support/reports/{reportId}/chat/messages?beforeId=&size=30
 * </pre>
 * 보내기·삭제·실시간 구독은 없다. 열람할 때마다 AUDIT_LOG 에 CHAT_VIEW 가 남는다.
 */
@RestController
@RequestMapping("/api/v1/admin/support/reports/{reportId}/chat")
public class AdminChatViewController {

    private final ChatService service;

    public AdminChatViewController(ChatService service) {
        this.service = service;
    }

    @GetMapping("/messages")
    public ApiResponse<ChatHistoryResponse> messages(Authentication authentication, @PathVariable long reportId,
                                                     @RequestParam(required = false) Long beforeId,
                                                     @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.adminHistory(adminId(authentication), reportId, beforeId, size), requestId());
    }

    /** 관리자 토큰만 허용 (관리자 토큰의 subject = ADMIN_ID) */
    static long adminId(Authentication authentication) {
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!admin) {
            throw new ChatException(HttpStatus.FORBIDDEN, "ADMIN_ONLY", "관리자만 이용할 수 있어요.");
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException e) {
            throw new ChatException(HttpStatus.FORBIDDEN, "ADMIN_ONLY", "관리자만 이용할 수 있어요.");
        }
    }

    private static String requestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
