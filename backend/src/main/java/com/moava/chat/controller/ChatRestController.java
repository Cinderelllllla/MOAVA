package com.moava.chat.controller;

import com.moava.chat.dto.ChatHistoryResponse;
import com.moava.chat.dto.ChatParticipant;
import com.moava.chat.dto.ChatRoom;
import com.moava.chat.service.ChatException;
import com.moava.chat.service.ChatService;
import com.moava.common.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * F34 채팅 보조 REST (실시간 송수신은 웹소켓).
 * <pre>
 * GET /api/v1/chat/rooms                                         내 채팅방(승인된 회원인 모임) 목록
 * GET /api/v1/chat/groups/{groupId}/messages?beforeId=&size=30    이전 메시지 (오래된 것 → 최신 순)
 * GET /api/v1/chat/groups/{groupId}/participants                 참여자(모임의 ACTIVE 회원) 목록
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatRestController {

    private final ChatService service;

    public ChatRestController(ChatService service) {
        this.service = service;
    }

    @GetMapping("/rooms")
    public ApiResponse<List<ChatRoom>> rooms(Authentication authentication) {
        return ApiResponse.ok(service.rooms(memberId(authentication)), requestId());
    }

    @GetMapping("/groups/{groupId}/messages")
    public ApiResponse<ChatHistoryResponse> messages(Authentication authentication, @PathVariable long groupId,
                                                     @RequestParam(required = false) Long beforeId,
                                                     @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.history(memberId(authentication), groupId, beforeId, size), requestId());
    }

    @GetMapping("/groups/{groupId}/participants")
    public ApiResponse<List<ChatParticipant>> participants(Authentication authentication, @PathVariable long groupId) {
        return ApiResponse.ok(service.participants(memberId(authentication), groupId), requestId());
    }

    /** 회원 토큰만 허용 (관리자 토큰의 subject 는 ADMIN_ID 라 회원 번호로 쓰면 안 됨) */
    static long memberId(Authentication authentication) {
        boolean member = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_MEMBER".equals(a.getAuthority()));
        if (!member) {
            throw new ChatException(HttpStatus.FORBIDDEN, "MEMBER_ONLY", "회원만 이용할 수 있어요.");
        }
        return Long.parseLong(authentication.getName());
    }

    private static String requestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
