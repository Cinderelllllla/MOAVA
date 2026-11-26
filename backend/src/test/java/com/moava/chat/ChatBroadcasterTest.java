package com.moava.chat;

import com.moava.chat.dto.ChatMessageResponse;
import com.moava.chat.service.ChatBroadcaster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 채팅 실시간 전송: 트랜잭션이 커밋된 뒤에만 보내고, 롤백되면 보내지 않는다 */
class ChatBroadcasterTest {

    SimpMessagingTemplate template;
    ChatBroadcaster broadcaster;
    final ChatMessageResponse notice = ChatMessageResponse.systemMessage(55L, 101L, "📌 안내", LocalDateTime.now());

    @BeforeEach
    void setUp() {
        template = mock(SimpMessagingTemplate.class);
        broadcaster = new ChatBroadcaster(template);
    }

    @AfterEach
    void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void 트랜잭션_밖이면_바로_보낸다() {
        broadcaster.broadcastAfterCommit(notice);
        verify(template).convertAndSend("/topic/groups/101/chat", notice);
    }

    @Test
    void 트랜잭션_안이면_커밋_뒤에만_보낸다() {
        TransactionSynchronizationManager.initSynchronization();
        broadcaster.broadcastAfterCommit(notice);
        verifyNoInteractions(template);   // 아직 커밋 전

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(template).convertAndSend("/topic/groups/101/chat", notice);
    }

    @Test
    void 롤백되면_보내지_않는다() {
        TransactionSynchronizationManager.initSynchronization();
        broadcaster.broadcastAfterCommit(notice);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(template);
    }

    @Test
    void 전송이_실패해도_예외를_밖으로_던지지_않는다() {
        doThrow(new RuntimeException("broker down")).when(template).convertAndSend(anyString(), any(Object.class));
        broadcaster.broadcastAfterCommit(notice);   // 저장은 이미 끝났으므로 조용히 로그만
    }
}
