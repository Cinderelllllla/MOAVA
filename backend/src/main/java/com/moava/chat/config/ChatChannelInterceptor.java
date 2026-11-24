package com.moava.chat.config;

import com.moava.chat.service.ChatException;
import com.moava.chat.service.ChatService;
import com.moava.member.service.JwtTokenProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 웹소켓 "문지기". 브라우저가 보내는 STOMP 명령마다 먼저 확인한다.
 * <ul>
 *   <li>CONNECT: Authorization 헤더의 JWT 확인. 회원(MEMBER) 토큰만 허용하고 회원 번호를 연결에 기억시킨다.</li>
 *   <li>SUBSCRIBE: /topic/groups/{id}/chat 은 그 모임의 승인된 회원만, 그 외엔 /user/queue/errors 만 허용.</li>
 *   <li>SEND: /app/groups/{id}/chat 만 허용 (/topic 으로 직접 보내 가짜 메시지를 뿌리는 것 차단).</li>
 * </ul>
 * 여기서 예외를 던지면 브라우저는 STOMP ERROR 를 받고 연결이 끊긴다.
 */
@Component
public class ChatChannelInterceptor implements ChannelInterceptor {

    static final Pattern ROOM_TOPIC = Pattern.compile("^/topic/groups/(\\d+)/chat$");
    static final Pattern ROOM_SEND = Pattern.compile("^/app/groups/(\\d+)/chat$");
    static final String ERROR_QUEUE = "/user/queue/errors";

    private final JwtTokenProvider jwt;
    private final ChatService chatService;

    public ChatChannelInterceptor(JwtTokenProvider jwt, ChatService chatService) {
        this.jwt = jwt;
        this.chatService = chatService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
        } else if (command == StompCommand.SUBSCRIBE) {
            checkSubscribe(memberId(accessor.getUser()), accessor.getDestination());
        } else if (command == StompCommand.SEND) {
            memberId(accessor.getUser());
            String destination = accessor.getDestination();
            if (destination == null || !ROOM_SEND.matcher(destination).matches()) {
                throw new MessageDeliveryException("보낼 수 없는 주소예요.");
            }
        }
        return message;
    }

    Principal authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new MessageDeliveryException("로그인이 필요해요.");
        }
        String token = authorization.substring(7);
        if (!jwt.isValid(token) || !"MEMBER".equals(jwt.getRole(token))) {
            throw new MessageDeliveryException("로그인 정보가 만료되었거나 유효하지 않아요. 다시 로그인해 주세요.");
        }
        return new UsernamePasswordAuthenticationToken(jwt.getId(token), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
    }

    void checkSubscribe(long memberId, String destination) {
        if (ERROR_QUEUE.equals(destination)) {
            return;
        }
        Matcher m = destination == null ? null : ROOM_TOPIC.matcher(destination);
        if (m == null || !m.matches()) {
            throw new MessageDeliveryException("구독할 수 없는 주소예요.");
        }
        try {
            chatService.requireReadable(memberId, Long.parseLong(m.group(1)));
        } catch (ChatException | NumberFormatException e) {
            throw new MessageDeliveryException(e instanceof ChatException ? e.getMessage() : "모임을 찾을 수 없어요.");
        }
    }

    static long memberId(Principal user) {
        if (user == null) {
            throw new MessageDeliveryException("로그인이 필요해요.");
        }
        return Long.parseLong(user.getName());
    }
}
