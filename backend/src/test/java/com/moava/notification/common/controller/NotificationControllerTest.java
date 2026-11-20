package com.moava.notification.common.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.moava.notification.common.dto.NotificationReceiver;
import com.moava.notification.common.service.NotificationException;

/** NotificationController.resolveReceiver — 로그인 정보로 받는 사람을 정하는 규칙 */
class NotificationControllerTest {

    private static Authentication member(String name, String role) {
        return new UsernamePasswordAuthenticationToken(name, null, List.of(new SimpleGrantedAuthority(role)));
    }

    private static void assertUnauthorized(Authentication auth) {
        assertThatThrownBy(() -> NotificationController.resolveReceiver(auth))
                .isInstanceOf(NotificationException.class)
                .extracting(e -> ((NotificationException) e).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("정상 회원 토큰이면 회원 알림함")
    void member_ok() {
        NotificationReceiver r = NotificationController.resolveReceiver(member("10", "ROLE_MEMBER"));
        assertThat(r.memberId()).isEqualTo(10L);
        assertThat(r.adminId()).isNull();
    }

    @Test
    @DisplayName("ROLE_ADMIN이 붙어 있어도 관리자 알림함으로 보지 않는다 (관리자 claim 도입 전)")
    void admin_role_is_not_admin_receiver() {
        NotificationReceiver r = NotificationController.resolveReceiver(member("3", "ROLE_ADMIN"));
        assertThat(r.memberId()).isEqualTo(3L);
        assertThat(r.adminId()).isNull();
    }

    @Test
    @DisplayName("로그인 정보가 없으면 401")
    void null_auth() {
        assertUnauthorized(null);
    }

    @Test
    @DisplayName("익명 사용자(anonymousUser)는 401")
    void anonymous() {
        assertUnauthorized(new AnonymousAuthenticationToken("key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
    }

    @Test
    @DisplayName("이름이 숫자가 아니거나 0·음수·범위 초과면 401")
    void invalid_names() {
        assertUnauthorized(member("user@moava.com", "ROLE_MEMBER"));
        assertUnauthorized(member("0", "ROLE_MEMBER"));
        assertUnauthorized(member("-5", "ROLE_MEMBER"));
        assertUnauthorized(member("99999999999999999999", "ROLE_MEMBER"));
    }
}
