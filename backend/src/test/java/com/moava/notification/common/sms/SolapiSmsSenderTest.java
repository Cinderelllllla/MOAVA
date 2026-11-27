package com.moava.notification.common.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** SolapiSmsSender — HMAC 서명, 설정 확인, 요청 형식 */
class SolapiSmsSenderTest {

    private static final String URL = "https://api.solapi.com/messages/v4/send";
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-11-24T08:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("HMAC-SHA256 hex (공개 테스트 벡터)")
    void hmac_known_vector() {
        assertThat(SolapiSmsSender.hmacSha256Hex("key", "The quick brown fox jumps over the lazy dog"))
                .isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
    }

    @Test
    @DisplayName("Authorization 헤더 형식: HMAC-SHA256 apiKey, date, salt, signature(date+salt)")
    void authorization_format() {
        String h = SolapiSmsSender.authorization("KEY", "SECRET", "2026-11-24T08:00:00Z", "abcdef0123456789");
        assertThat(h).startsWith("HMAC-SHA256 apiKey=KEY, date=2026-11-24T08:00:00Z, salt=abcdef0123456789, signature=");
        assertThat(h).endsWith(SolapiSmsSender.hmacSha256Hex("SECRET", "2026-11-24T08:00:00Z" + "abcdef0123456789"));
    }

    @Test
    @DisplayName("salt는 요청마다 다른 32자")
    void salt_random() {
        String a = SolapiSmsSender.newSalt(), b = SolapiSmsSender.newSalt();
        assertThat(a).hasSize(32).isNotEqualTo(b);
    }

    @Test
    @DisplayName("키가 비었거나 노션 placeholder(★)면 미설정, 발송하지 않는다")
    void placeholder_is_not_configured() {
        SolapiSmsSender s = new SolapiSmsSender("★SOLAPI_KEY", "★SOLAPI_SECRET", "★발신번호", URL,
                RestClient.create(), FIXED);
        assertThat(s.isConfigured()).isFalse();
        assertThat(s.send("01012345678", "hi")).isFalse();
    }

    @Test
    @DisplayName("정상 발송: POST, 인증 헤더, 번호는 숫자만")
    void send_ok() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL))
              .andExpect(method(HttpMethod.POST))
              .andExpect(header("Authorization", Matchers.startsWith("HMAC-SHA256 apiKey=KEY, date=2026-11-24T08:00:00Z, salt=")))
              .andExpect(content().string(Matchers.containsString("\"to\":\"01012345678\"")))
              .andExpect(content().string(Matchers.containsString("\"from\":\"0212345678\"")))
              .andRespond(withSuccess("{\"statusCode\":\"2000\",\"statusMessage\":\"정상 접수\",\"messageId\":\"M4VTEST\"}", MediaType.APPLICATION_JSON));

        SolapiSmsSender s = new SolapiSmsSender("KEY", "SECRET", "02-1234-5678", URL, builder.build(), FIXED);
        assertThat(s.send("010-1234-5678", "[MOAVA] 테스트")).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("SOLAPI 오류 응답이면 false (예외를 던지지 않음)")
    void send_server_error() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withServerError());

        SolapiSmsSender s = new SolapiSmsSender("KEY", "SECRET", "0212345678", URL, builder.build(), FIXED);
        assertThat(s.send("01012345678", "x")).isFalse();
    }

    @Test
    @DisplayName("HTTP 200이어도 statusCode가 2000이 아니면 false")
    void send_not_accepted() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL))
              .andRespond(withSuccess("{\"statusCode\":\"3059\",\"statusMessage\":\"변작된 발신번호\",\"messageId\":\"M4VFAIL\"}", MediaType.APPLICATION_JSON));

        SolapiSmsSender s = new SolapiSmsSender("KEY", "SECRET", "0212345678", URL, builder.build(), FIXED);
        assertThat(s.send("01012345678", "x")).isFalse();
    }

    @Test
    @DisplayName("응답 판정: 2000만 성공, 빈 응답·다른 코드는 실패")
    void accepted_rule() {
        assertThat(SolapiSmsSender.accepted(java.util.Map.of("statusCode", "2000"))).isTrue();
        assertThat(SolapiSmsSender.accepted(java.util.Map.of("statusCode", "4000"))).isFalse();
        assertThat(SolapiSmsSender.accepted(java.util.Map.of())).isFalse();
        assertThat(SolapiSmsSender.accepted(null)).isFalse();
    }
}
