package com.moava.group.map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * F13 / F15 — Kakao Local REST API 지오코딩 클라이언트.
 * <ul>
 *   <li>시/도 + 시/군/구 문자열을 주소 검색({@code /v2/local/search/address.json})으로 조회한다.</li>
 *   <li>주소 검색 결과가 비어 있으면 키워드 검색({@code /v2/local/search/keyword.json})으로 폴백한다.</li>
 *   <li>REST API 키가 비어 있거나 Kakao API 호출에 실패해도 모임 생성을 막지 않고 {@link Optional#empty()}를 반환한다.</li>
 * </ul>
 * REST API 키 값은 절대 로그에 남기지 않는다.
 */
@Component
public class KakaoGeocodingClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoGeocodingClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {};
    private static final String AUTH_HEADER = "Authorization";
    private static final String ADDRESS_SEARCH_PATH = "/v2/local/search/address.json";
    private static final String KEYWORD_SEARCH_PATH = "/v2/local/search/keyword.json";
    private static final int COORD_SCALE = 7;

    private final RestClient restClient;
    private final boolean enabled;

    @Autowired
    public KakaoGeocodingClient(
            @Value("${moava.api.kakao.base-url:https://dapi.kakao.com}") String baseUrl,
            @Value("${moava.api.kakao.rest-key:}") String restApiKey
    ) {
        this(RestClient.builder().requestFactory(timeouts()), baseUrl, restApiKey);
    }

    /** 테스트에서 MockRestServiceServer 를 붙일 수 있는 생성자 */
    public KakaoGeocodingClient(
            RestClient.Builder builder,
            String baseUrl,
            String restApiKey
    ) {
        String key = restApiKey == null ? "" : restApiKey.strip();
        this.enabled = !key.isEmpty();
        if (!this.enabled) {
            log.warn("moava.api.kakao.rest-key 가 비어 있어요. application-secret.properties 에 Kakao REST API 키를 설정해야 지오코딩이 동작해요.");
        } else {
            builder.defaultHeader(AUTH_HEADER, "KakaoAK " + key);
        }
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(3));
        return factory;
    }

    /** 시/도와 시/군/구를 조합해 대표 좌표를 조회한다. */
    public Optional<GeoPoint> geocodeRegion(String regionSido, String regionSigungu) {
        String sido = regionSido == null ? "" : regionSido.strip();
        String sigungu = regionSigungu == null ? "" : regionSigungu.strip();
        String query = (sido + " " + sigungu).strip();
        return geocode(query);
    }

    /** 주소/지역 문자열을 위경도로 변환한다. 실패 시 빈 Optional 반환. */
    public Optional<GeoPoint> geocode(String query) {
        if (!enabled) {
            return Optional.empty();
        }
        String normalized = query == null ? "" : query.strip();
        if (normalized.isEmpty()) {
            return Optional.empty();
        }

        Optional<GeoPoint> byAddress = search(ADDRESS_SEARCH_PATH, normalized);
        if (byAddress.isPresent()) {
            return byAddress;
        }
        return search(KEYWORD_SEARCH_PATH, normalized);
    }

    private Optional<GeoPoint> search(String path, String query) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(path)
                            .queryParam("query", query)
                            .queryParam("size", 1)
                            .build())
                    .retrieve()
                    .body(JSON_OBJECT);

            return extractFirstPoint(body);
        } catch (RestClientException e) {
            log.warn("Kakao Local 지오코딩 호출 실패 path={} query={} cause={}",
                    path, query, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<GeoPoint> extractFirstPoint(Map<String, Object> body) {
        if (body == null) {
            return Optional.empty();
        }
        Object docsObj = body.get("documents");
        if (!(docsObj instanceof List<?> docs) || docs.isEmpty()) {
            return Optional.empty();
        }
        Object first = docs.get(0);
        if (!(first instanceof Map<?, ?> doc)) {
            return Optional.empty();
        }

        // Kakao Local API: y = 위도(LAT), x = 경도(LNG)
        BigDecimal lat = parseCoordinate(doc.get("y"), -90, 90);
        BigDecimal lng = parseCoordinate(doc.get("x"), -180, 180);
        if (lat == null || lng == null) {
            return Optional.empty();
        }
        return Optional.of(new GeoPoint(lat, lng));
    }

    private static BigDecimal parseCoordinate(Object raw, int min, int max) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).strip();
        if (text.isEmpty()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(text).setScale(COORD_SCALE, RoundingMode.HALF_UP);
            if (value.compareTo(BigDecimal.valueOf(min)) < 0
                    || value.compareTo(BigDecimal.valueOf(max)) > 0) {
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
