package com.moava.notification.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.moava.notification.common.mapper.NotificationCleanupMapper;

/** NotificationCleanupService — 보관 기간(읽음 30일·안 읽음 90일)으로 숨김 쿼리를 부르는지 확인 */
@ExtendWith(MockitoExtension.class)
class NotificationCleanupServiceTest {

    @Mock
    NotificationCleanupMapper mapper;

    NotificationCleanupService service;

    @BeforeEach
    void setUp() {
        service = new NotificationCleanupService(mapper, true);
    }

    @Test
    @DisplayName("읽음 30일, 안 읽음 90일 기준으로 숨기고 건수를 돌려준다")
    void cleanup_uses_retention_days() {
        when(mapper.hideOldRead(30)).thenReturn(3);
        when(mapper.hideOldUnread(90)).thenReturn(1);

        NotificationCleanupService.CleanupResult r = service.cleanup();

        assertThat(r.hiddenRead()).isEqualTo(3);
        assertThat(r.hiddenUnread()).isEqualTo(1);
        assertThat(r.total()).isEqualTo(4);
        InOrder order = inOrder(mapper);
        order.verify(mapper).hideOldRead(30);
        order.verify(mapper).hideOldUnread(90);
    }

    @Test
    @DisplayName("정리할 알림이 없으면 0건")
    void cleanup_nothing() {
        NotificationCleanupService.CleanupResult r = service.cleanup();
        assertThat(r.total()).isZero();
    }

    @Test
    @DisplayName("enabled=false면 쿼리를 실행하지 않는다")
    void cleanup_disabled() {
        NotificationCleanupService off = new NotificationCleanupService(mapper, false);
        assertThat(off.cleanup().total()).isZero();
        verifyNoInteractions(mapper);
    }
}
