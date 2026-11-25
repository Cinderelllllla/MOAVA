package com.moava.notification.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.moava.member.service.PhoneCryptoService;
import com.moava.notification.common.domain.NotifType;
import com.moava.notification.common.dto.NotificationSmsEvent;
import com.moava.notification.common.mapper.NotificationSmsMapper;
import com.moava.notification.common.service.NotificationSmsListener.Result;
import com.moava.notification.common.sms.SmsSender;

/** NotificationSmsListener — 켜기/끄기, 대상 회원 확인, 발송 기록 */
@ExtendWith(MockitoExtension.class)
class NotificationSmsListenerTest {

    @Mock NotificationSmsMapper mapper;
    @Mock PhoneCryptoService phoneCrypto;
    @Mock SmsSender sender;

    private static final NotificationSmsEvent EVENT =
            new NotificationSmsEvent(7L, 10L, NotifType.PAY_DUE_3D, "다음 납입일이 다가와요");

    private NotificationSmsListener listener(boolean enabled) {
        return new NotificationSmsListener(mapper, phoneCrypto, sender, enabled);
    }

    private static Map<String, Object> target(String status, String phoneEnc) {
        Map<String, Object> m = new HashMap<>();
        m.put("status", status);
        m.put("phoneEnc", phoneEnc);
        m.put("keyVer", 1);
        return m;
    }

    @Test
    @DisplayName("moava.sms.enabled=false면 아무것도 하지 않는다")
    void disabled() {
        assertThat(listener(false).handle(EVENT)).isEqualTo(Result.DISABLED);
        verifyNoInteractions(mapper, phoneCrypto);
        verify(sender, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("켜져 있어도 SOLAPI 키가 없으면 보내지 않는다")
    void not_configured() {
        when(sender.isConfigured()).thenReturn(false);
        assertThat(listener(true).handle(EVENT)).isEqualTo(Result.DISABLED);
        verifyNoInteractions(mapper, phoneCrypto);
    }

    @Test
    @DisplayName("정상: 번호 복호화 → [MOAVA] 본문 발송 → SMS_SENT_YN 기록")
    void sent() {
        when(sender.isConfigured()).thenReturn(true);
        when(mapper.findSmsTarget(10L)).thenReturn(target("ACTIVE", "ENC"));
        when(phoneCrypto.decrypt("ENC", 1)).thenReturn("010-1234-5678");
        when(sender.send("010-1234-5678", "[MOAVA] 다음 납입일이 다가와요")).thenReturn(true);

        assertThat(listener(true).handle(EVENT)).isEqualTo(Result.SENT);
        verify(mapper).markSmsSent(7L);
    }

    @Test
    @DisplayName("탈퇴(WITHDRAWN) 회원은 복호화·발송하지 않는다")
    void inactive_member() {
        when(sender.isConfigured()).thenReturn(true);
        when(mapper.findSmsTarget(10L)).thenReturn(target("WITHDRAWN", null));

        assertThat(listener(true).handle(EVENT)).isEqualTo(Result.INACTIVE);
        verifyNoInteractions(phoneCrypto);
        verify(sender, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("번호가 없으면 건너뛴다")
    void no_phone() {
        when(sender.isConfigured()).thenReturn(true);
        when(mapper.findSmsTarget(10L)).thenReturn(target("ACTIVE", " "));

        assertThat(listener(true).handle(EVENT)).isEqualTo(Result.NO_PHONE);
        verify(sender, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("회원이 없으면 건너뛴다")
    void no_member() {
        when(sender.isConfigured()).thenReturn(true);
        when(mapper.findSmsTarget(10L)).thenReturn(null);
        assertThat(listener(true).handle(EVENT)).isEqualTo(Result.NO_MEMBER);
    }

    @Test
    @DisplayName("발송 실패면 SMS_SENT_YN을 바꾸지 않는다")
    void send_failed() {
        when(sender.isConfigured()).thenReturn(true);
        when(mapper.findSmsTarget(10L)).thenReturn(target("ACTIVE", "ENC"));
        when(phoneCrypto.decrypt("ENC", 1)).thenReturn("01012345678");
        when(sender.send(anyString(), anyString())).thenReturn(false);

        assertThat(listener(true).handle(EVENT)).isEqualTo(Result.FAILED);
        verify(mapper, never()).markSmsSent(anyLong());
    }

    @Test
    @DisplayName("복호화 오류도 예외를 밖으로 던지지 않는다")
    void decrypt_error_swallowed() {
        when(sender.isConfigured()).thenReturn(true);
        when(mapper.findSmsTarget(10L)).thenReturn(target("ACTIVE", "ENC"));
        when(phoneCrypto.decrypt("ENC", 1)).thenThrow(new IllegalStateException("bad key"));

        assertThat(listener(true).handle(EVENT)).isEqualTo(Result.FAILED);
        verify(mapper, never()).markSmsSent(anyLong());
    }
}
