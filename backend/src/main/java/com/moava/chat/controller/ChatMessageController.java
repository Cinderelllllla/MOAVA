package com.moava.chat.controller;

import com.moava.chat.dto.ChatMessageResponse;
import com.moava.chat.dto.ChatSendRequest;
import com.moava.chat.service.ChatException;
import com.moava.chat.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * STOMP 메시지 받기: SEND /app/groups/{groupId}/chat {"body":"..."}
 * → 검사·암호화·저장(ChatService) → /topic/groups/{groupId}/chat 으로 방 전체에 방송.
 * 실패하면 보낸 사람에게만 /user/queue/errors 로 {"code","message"} 를 돌려준다.
 */
@Controller
public class ChatMessageController {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageController.class);

    private final ChatService service;
    private final SimpMessagingTemplate template;

    public ChatMessageController(ChatService service, SimpMessagingTemplate template) {
        this.service = service;
        this.template = template;
    }

    @MessageMapping("/groups/{groupId}/chat")
    public void send(@DestinationVariable long groupId, @Payload ChatSendRequest request, Principal principal) {
        long memberId = Long.parseLong(principal.getName());
        ChatMessageResponse saved = service.send(memberId, groupId, request == null ? null : request.body());
        template.convertAndSend(topic(groupId), saved);
    }

    public static String topic(long groupId) {
        return "/topic/groups/" + groupId + "/chat";
    }

    @MessageExceptionHandler(ChatException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public Map<String, String> chatError(ChatException e) {
        return Map.of("code", e.getCode(), "message", e.getMessage());
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public Map<String, String> unexpected(Exception e) {
        log.warn("채팅 메시지 처리 실패: {}", e.getClass().getSimpleName());   // 본문은 남기지 않는다
        return Map.of("code", "CHAT_SEND_FAILED", "message", "메시지를 보내지 못했어요. 잠시 후 다시 시도해 주세요.");
    }
}
