package com.moava.finance.service;

import com.moava.finance.dto.KosisHouseholdRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * F07 용 KOSIS 소득10분위별 가계수지 수집기 (REQ-DATA-02).
 * - 서버 기동 직후 1번 + 매월 1일 04:10 (서울 시간)에 KOSIS 를 불러 KOSIS_HOUSEHOLD 를 갱신한다.
 * - 설정(application-secret.properties, 깃에 올리지 않음):
 *     moava.kosis.api-key  = 발급받은 인증키
 *     moava.kosis.data-url = KOSIS 에서 만든 통계자료 URL (apiKey 는 빼고 넣어도 됨, format=json)
 *   둘 중 하나라도 없으면 아무것도 하지 않는다 (F07 은 available=false 로 동작).
 * - 실패해도 서버는 계속 돌고, 기존 DB 데이터는 그대로 둔다. 로그에 인증키는 남기지 않는다.
 */
@Component
public class KosisHouseholdCollector {

    private static final Logger log = LoggerFactory.getLogger(KosisHouseholdCollector.class);

    private final KosisHouseholdWriter writer;
    private final RestClient restClient;
    private final String apiKey;
    private final String dataUrl;

    public KosisHouseholdCollector(KosisHouseholdWriter writer,
                                   @Value("${moava.kosis.api-key:}") String apiKey,
                                   @Value("${moava.kosis.data-url:}") String dataUrl) {
        this.writer = writer;
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.dataUrl = dataUrl == null ? "" : dataUrl.strip();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        refreshSafely("서버 기동");
    }

    @Scheduled(cron = "0 10 4 1 * *", zone = "Asia/Seoul")
    public void monthly() {
        refreshSafely("월간 배치");
    }

    void refreshSafely(String trigger) {
        if (apiKey.isEmpty() || dataUrl.isEmpty()) {
            log.info("[KOSIS] {}: moava.kosis.api-key / moava.kosis.data-url 설정이 없어 수집을 건너뜁니다.", trigger);
            return;
        }
        try {
            List<KosisHouseholdRow> rows = fetch();
            writer.saveAll(rows);
            log.info("[KOSIS] {}: 소득 10분위 가계수지 {}건 갱신 (기준 {})", trigger, rows.size(), rows.get(0).baseYm());
        } catch (Exception e) {
            log.warn("[KOSIS] {}: 갱신 실패, 기존 데이터를 유지합니다. 원인: {}", trigger, mask(e.getMessage()));
        }
    }

    List<KosisHouseholdRow> fetch() {
        byte[] bytes = restClient.get().uri(URI.create(requestUrl())).retrieve().body(byte[].class);
        String body = bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
        List<Map<String, String>> parsed = KosisJsonParser.parseRows(body);
        return KosisHouseholdAssembler.assemble(parsed);
    }

    private String requestUrl() {
        if (dataUrl.contains("apiKey=")) {
            return dataUrl;
        }
        String sep = dataUrl.contains("?") ? "&" : "?";
        return dataUrl + sep + "apiKey=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
    }

    static String mask(String message) {
        return message == null ? null : message.replaceAll("apiKey=[^&\"'\\s]*", "apiKey=***");
    }
}
