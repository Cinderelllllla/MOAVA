package com.moava.chat;

import com.moava.chat.config.ChatChannelInterceptor;
import com.moava.chat.dto.ChatAccess;
import com.moava.chat.service.ChatException;
import com.moava.chat.service.ChatService;
import com.moava.member.service.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatChannelInterceptorTest {

    @Mock JwtTokenProvider jwt;
    @Mock ChatService chatService;
    @InjectMocks ChatChannelInterceptor interceptor;

    private static final Principal ME = new UsernamePasswordAuthenticationToken("7", null);

    private Message<byte[]> frame(StompCommand command, String destination, String authorization, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) accessor.setDestination(destination);
        if (authorization != null) accessor.addNativeHeader("Authorization", authorization);
        if (user != null) accessor.setUser(user);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private void rejects(Message<?> message) {
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(message, null));
    }

    // ---- CONNECT ----

    @Test
    void 토큰_없이_연결하면_거부() {
        rejects(frame(StompCommand.CONNECT, null, null, null));
    }

    @Test
    void 만료되거나_위조된_토큰은_거부() {
        when(jwt.isValid("bad")).thenReturn(false);
        rejects(frame(StompCommand.CONNECT, null, "Bearer bad", null));
    }

    @Test
    void 관리자_토큰은_거부() {   // 관리자 subject 는 ADMIN_ID 라 회원 번호로 쓰면 안 됨
        when(jwt.isValid("admin")).thenReturn(true);
        when(jwt.getRole("admin")).thenReturn("ADMIN");
        rejects(frame(StompCommand.CONNECT, null, "Bearer admin", null));
    }

    @Test
    void 회원_토큰이면_연결에_회원_번호를_기억시킨다() {
        when(jwt.isValid("ok")).thenReturn(true);
        when(jwt.getRole("ok")).thenReturn("MEMBER");
        when(jwt.getId("ok")).thenReturn("7");
        Message<?> out = interceptor.preSend(frame(StompCommand.CONNECT, null, "Bearer ok", null), null);
        assertEquals("7", StompHeaderAccessor.wrap(out).getUser().getName());
    }

    // ---- SUBSCRIBE ----

    @Test
    void 내_모임_방은_구독_가능() {
        when(chatService.requireReadable(7L, 101L)).thenReturn(new ChatAccess("ACTIVE", "ACTIVE", "N", null));
        assertDoesNotThrow(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/groups/101/chat", null, ME), null));
    }

    @Test
    void 남의_모임_방은_구독_거부() {
        when(chatService.requireReadable(7L, 202L))
                .thenThrow(new ChatException(HttpStatus.FORBIDDEN, "NOT_GROUP_MEMBER", "이 모임 회원만 채팅방에 들어갈 수 있어요."));
        rejects(frame(StompCommand.SUBSCRIBE, "/topic/groups/202/chat", null, ME));
    }

    @Test
    void 정해진_주소_외에는_구독_거부() {
        rejects(frame(StompCommand.SUBSCRIBE, "/topic/groups/101/other", null, ME));
        rejects(frame(StompCommand.SUBSCRIBE, "/topic/**", null, ME));
        verifyNoInteractions(chatService);
    }

    @Test
    void 내_오류_큐는_구독_가능() {
        assertDoesNotThrow(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/errors", null, ME), null));
    }

    @Test
    void 로그인_없이_구독하면_거부() {
        rejects(frame(StompCommand.SUBSCRIBE, "/topic/groups/101/chat", null, null));
    }

    // ---- SEND ----

    @Test
    void topic_으로_직접_보내서_가짜_메시지를_뿌리는_것은_차단() {
        rejects(frame(StompCommand.SEND, "/topic/groups/101/chat", null, ME));
    }

    @Test
    void 채팅_보내기_주소는_허용() {
        assertDoesNotThrow(() -> interceptor.preSend(frame(StompCommand.SEND, "/app/groups/101/chat", null, ME), null));
    }
}
