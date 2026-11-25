package com.moava.notification.common.sms;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * SOLAPI 문자 발송 (F36). https://solapi.com/developers/api/authentication-api-key
 *
 * <p>인증: Authorization: HMAC-SHA256 apiKey=…, date=…, salt=…, signature=hex(HMAC-SHA256(secret, date+salt))
 * <p>설정(application-secret.properties): moava.api.solapi.key / secret / sender
 *    값이 비었거나 노션 placeholder(★…)면 미설정으로 보고 발송하지 않는다.
 * <p>전화번호·키는 로그에 남기지 않는다.
 */
@Component
public class SolapiSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(SolapiSmsSender.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String apiKey;
    private final String apiSecret;
    private final String sender;
    private final String sendUrl;
    private final RestClient restClient;
    private final Clock clock;

    @Autowired
    public SolapiSmsSender(
            @Value("${moava.api.solapi.key:}") String apiKey,
            @Value("${moava.api.solapi.secret:}") String apiSecret,
            @Value("${moava.api.solapi.sender:}") String sender,
            @Value("${moava.api.solapi.send-url:https://api.solapi.com/messages/v4/send}") String sendUrl) {
        this(apiKey, apiSecret, sender, sendUrl, RestClient.create(), Clock.systemUTC());
    }

    SolapiSmsSender(String apiKey, String apiSecret, String sender, String sendUrl,
                    RestClient restClient, Clock clock) {
        this.apiKey = clean(apiKey);
        this.apiSecret = clean(apiSecret);
        this.sender = digits(clean(sender));
        this.sendUrl = sendUrl;
        this.restClient = restClient;
        this.clock = clock;
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isEmpty() && !apiSecret.isEmpty() && !sender.isEmpty();
    }

    @Override
    public boolean send(String to, String text) {
        if (!isConfigured()) {
            log.warn("[SMS] SOLAPI 키·발신번호 미설정이라 발송하지 않음");
            return false;
        }
        try {
            String date = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS).toString();
            String salt = newSalt();
            restClient.post()
                    .uri(sendUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", authorization(apiKey, apiSecret, date, salt))
                    .body(Map.of("message", Map.of("to", digits(to), "from", sender, "text", text)))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            // 응답 본문에 번호가 섞일 수 있어 예외 종류만 남긴다
            log.warn("[SMS] SOLAPI 발송 실패: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    /** Authorization 헤더 값. signature = hex(HMAC-SHA256(secret, date + salt)) */
    static String authorization(String apiKey, String apiSecret, String date, String salt) {
        return "HMAC-SHA256 apiKey=" + apiKey + ", date=" + date + ", salt=" + salt
                + ", signature=" + hmacSha256Hex(apiSecret, date + salt);
    }

    static String hmacSha256Hex(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 계산 실패", e);
        }
    }

    /** 요청마다 다른 32자 salt (SOLAPI 요구: 12~64바이트, 매 요청 다르게) */
    static String newSalt() {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    private static String clean(String v) {
        if (v == null) return "";
        String t = v.trim();
        return t.startsWith("★") ? "" : t;   // 노션 placeholder는 미설정 취급
    }

    static String digits(String v) {
        return v == null ? "" : v.replaceAll("[^0-9]", "");
    }
}
