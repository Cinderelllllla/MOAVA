package com.moava.finance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * ML 서버(FastAPI) 호출 담당. 브라우저는 ML 을 직접 부르지 않고 Spring 이 대신 부른다.
 * <ul>
 *   <li>주소: moava.ml.base-url, 연결·읽기 타임아웃 3초</li>
 *   <li>모든 요청에 X-Internal-Key(공유 키, moava.ml.internal-key)를 붙인다. ML 은 키가 다르면 401.</li>
 *   <li>ML 오류는 FinanceException 으로 바꿔서 화면에 쉬운 문장이 나가게 한다.</li>
 * </ul>
 * 공유 키 값은 절대 로그에 남기지 않는다.
 */
@Component
public class MlClient {

    private static final Logger log = LoggerFactory.getLogger(MlClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {};

    static final String KEY_HEADER = "X-Internal-Key";
    static final String UNAVAILABLE = "분석 서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.";
    static final String CHECK_INPUT = "입력한 금융정보를 확인해 주세요.";

    private final RestClient restClient;

    @Autowired
    public MlClient(@Value("${moava.ml.base-url:http://localhost:8000}") String baseUrl,
                    @Value("${moava.ml.internal-key:}") String internalKey) {
        this(RestClient.builder().requestFactory(timeouts()), baseUrl, internalKey);
    }

    /** 테스트에서 가짜 ML 서버(MockRestServiceServer)를 붙일 수 있게 builder 를 받는 생성자 */
    public MlClient(RestClient.Builder builder, String baseUrl, String internalKey) {
        String key = internalKey == null ? "" : internalKey.strip();
        if (key.isEmpty()) {
            log.warn("moava.ml.internal-key 가 비어 있어요. application-secret.properties 에 ML 공유 키를 넣어야 AI 분석이 동작해요.");
        } else {
            builder.defaultHeader(KEY_HEADER, key);
        }
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(3));
        return factory;
    }

    /** POST /risk/contribution — body 는 {member_id, monthly_income, monthly_expense, monthly_saving, emergency_fund} (원) */
    public Map<String, Object> contribution(Map<String, Object> body) {
        return call("/risk/contribution", () -> restClient.post().uri("/risk/contribution")
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(JSON_OBJECT));
    }

    /** GET /risk/model — 모델 정보 + 정책 값 */
    public Map<String, Object> model() {
        return call("/risk/model", () -> restClient.get().uri("/risk/model")
                .retrieve().body(JSON_OBJECT));
    }

    /** POST /risk/payout-order — 모임원 회원 번호 2~100명 */
    public Map<String, Object> payoutOrder(List<Long> memberIds) {
        return call("/risk/payout-order", () -> restClient.post().uri("/risk/payout-order")
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("member_ids", memberIds))
                .retrieve().body(JSON_OBJECT));
    }

    /** ML 호출 + 오류 변환. 로그에는 경로와 오류 종류만 남긴다 (공유 키·금융 정보 X). */
    private Map<String, Object> call(String path, Supplier<Map<String, Object>> request) {
        try {
            Map<String, Object> body = request.get();
            if (body == null) {
                throw unavailable();
            }
            return body;
        } catch (ResourceAccessException e) {
            // 연결 거부(ML 꺼짐)·타임아웃
            log.warn("ML 서버 연결 실패 path={} cause={}", path, e.getClass().getSimpleName());
            throw unavailable();
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403) {
                // 사용자 잘못이 아니라 서버 설정 문제 → 사용자에게는 "연결 실패"로 안내
                log.error("ML 서버가 공유 키를 거부했어요 (HTTP {}). moava.ml.internal-key 와 ML 의 MOAVA_INTERNAL_KEY 가 같은지 확인하세요.", status);
                throw unavailable();
            }
            log.info("ML 서버 입력 오류 path={} status={}", path, status);
            throw new FinanceException(HttpStatus.BAD_REQUEST, "ML_BAD_REQUEST", koreanDetail(e));
        } catch (HttpServerErrorException e) {
            log.warn("ML 서버 오류 path={} status={}", path, e.getStatusCode().value());
            throw unavailable();
        } catch (RestClientException e) {
            // 응답을 읽지 못한 경우 등
            log.warn("ML 서버 응답 처리 실패 path={} cause={}", path, e.getClass().getSimpleName());
            throw unavailable();
        }
    }

    /** ML 이 준 detail 이 한국어 문장이면 그대로, 아니면(영어 검증 오류 목록 등) 기본 안내 */
    private static String koreanDetail(HttpClientErrorException e) {
        try {
            Map<?, ?> body = e.getResponseBodyAs(Map.class);
            Object detail = body == null ? null : body.get("detail");
            if (detail instanceof String s && s.matches("(?s).*[가-힣].*")) {
                return s;
            }
        } catch (RuntimeException ignored) {
            // 본문이 JSON 이 아니면 기본 안내
        }
        return CHECK_INPUT;
    }

    private static FinanceException unavailable() {
        return new FinanceException(HttpStatus.SERVICE_UNAVAILABLE, "ML_UNAVAILABLE", UNAVAILABLE);
    }
}
