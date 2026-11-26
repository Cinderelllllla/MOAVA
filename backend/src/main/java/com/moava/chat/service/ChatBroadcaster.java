package com.moava.chat.service;

import com.moava.chat.controller.ChatMessageController;
import com.moava.chat.dto.ChatMessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 채팅방(/topic/groups/{groupId}/chat)으로 메시지를 실시간 전송한다.
 *
 * <p>트랜잭션 안에서 부르면 <b>커밋이 끝난 뒤에만</b> 보낸다. 롤백되면 보내지 않는다.
 * (DB 에 없는 메시지가 화면에만 뜨는 일을 막기 위해)
 * 트랜잭션 밖에서 부르면 바로 보낸다.
 */
@Component
public class ChatBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(ChatBroadcaster.class);

    private final SimpMessagingTemplate template;

    public ChatBroadcaster(SimpMessagingTemplate template) {
        this.template = template;
    }

    public void broadcastAfterCommit(ChatMessageResponse message) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(message);
                }
            });
        } else {
            send(message);
        }
    }

    private void send(ChatMessageResponse message) {
        try {
            template.convertAndSend(ChatMessageController.topic(message.groupId()), message);
        } catch (RuntimeException e) {
            // 저장은 이미 끝났다. 실시간 전송만 실패한 것이라 채팅방을 다시 열면 보인다. 본문은 로그에 남기지 않는다.
            log.warn("채팅 실시간 전송 실패 groupId={} messageId={} cause={}",
                    message.groupId(), message.messageId(), e.getClass().getSimpleName());
        }
    }
}
