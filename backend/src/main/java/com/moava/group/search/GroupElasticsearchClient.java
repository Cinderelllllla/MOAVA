package com.moava.group.search;

import com.moava.group.search.dto.GroupSearchDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * F11/F12 — Elasticsearch REST API 클라이언트 ({@code moava_group} 인덱스).
 * <ul>
 *   <li>모든 검색·색인 호출은 짧은 타임아웃(연결 1.5초 / 읽기 2.5초)으로 격리되어 ES 장애 시 본 서비스에 영향을 주지 않는다.</li>
 *   <li>검색 실패 시 {@link Optional#empty()}를 반환하여 서비스 레이어가 DB {@code LIKE} 폴백을 수행할 수 있게 한다.</li>
 *   <li>ES 정상 응답으로 0건이 조회된 경우에는 {@code Optional.of(List.of())}를 반환한다.</li>
 * </ul>
 */
@Component
public class GroupElasticsearchClient {

    private static final Logger log = LoggerFactory.getLogger(GroupElasticsearchClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {};
    private static final MediaType NDJSON = new MediaType("application", "x-ndjson", StandardCharsets.UTF_8);
    private static final int SEARCH_SIZE = 200;

    private final RestClient restClient;
    private final boolean enabled;
    private final String indexName;

    @Autowired
    public GroupElasticsearchClient(
            @Value("${moava.elasticsearch.enabled:true}") boolean enabled,
            @Value("${moava.elasticsearch.base-url:http://localhost:9200}") String baseUrl,
            @Value("${moava.elasticsearch.index-name:moava_group}") String indexName
    ) {
        this(RestClient.builder().requestFactory(timeouts()), enabled, baseUrl, indexName);
    }

    /** 테스트에서 MockRestServiceServer 를 붙일 수 있는 생성자 */
    public GroupElasticsearchClient(
            RestClient.Builder builder,
            boolean enabled,
            String baseUrl,
            String indexName
    ) {
        String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.strip();
        String normalizedIndex = indexName == null ? "" : indexName.strip();
        this.enabled = enabled && !normalizedBaseUrl.isEmpty() && !normalizedIndex.isEmpty();
        this.indexName = normalizedIndex.isEmpty() ? "moava_group" : normalizedIndex;
        this.restClient = builder.baseUrl(normalizedBaseUrl.isEmpty() ? "http://localhost:9200" : normalizedBaseUrl).build();
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(1500));
        factory.setReadTimeout(Duration.ofMillis(2500));
        return factory;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String indexName() {
        return indexName;
    }

    /** {@code moava_group} 인덱스가 없으면 nori 설정·매핑으로 생성한다. */
    public boolean ensureIndex() {
        if (!enabled) {
            return false;
        }
        try {
            restClient.head()
                    .uri("/{index}", indexName)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound notFound) {
            return createIndex();
        } catch (RestClientException e) {
            log.warn("Elasticsearch 인덱스 확인 실패 index={} cause={}", indexName, e.getClass().getSimpleName());
            return false;
        }
    }

    private boolean createIndex() {
        try {
            restClient.put()
                    .uri("/{index}", indexName)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(GroupSearchIndexDefinition.indexBody())
                    .retrieve()
                    .toBodilessEntity();
            log.info("Elasticsearch 인덱스 생성 완료 index={}", indexName);
            return true;
        } catch (RestClientException e) {
            log.warn("Elasticsearch 인덱스 생성 실패 index={} cause={}", indexName, e.getClass().getSimpleName());
            return false;
        }
    }

    /** 단건 공개 모임 문서를 색인(upsert)한다. */
    public boolean indexDocument(GroupSearchDocument doc) {
        if (!enabled || doc == null || doc.groupId() == null) {
            return false;
        }
        try {
            restClient.put()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{index}/_doc/{id}")
                            .queryParam("refresh", "true")
                            .build(indexName, doc.groupId()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(doc)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            log.warn("Elasticsearch 문서 색인 실패 groupId={} cause={}", doc.groupId(), e.getClass().getSimpleName());
            return false;
        }
    }

    /** 해산·비공개 전환된 모임 문서를 인덱스에서 제거한다. (이미 없는 404도 성공 처리) */
    public boolean deleteDocument(Long groupId) {
        if (!enabled || groupId == null) {
            return false;
        }
        try {
            restClient.delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{index}/_doc/{id}")
                            .queryParam("refresh", "true")
                            .build(indexName, groupId))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound ignored) {
            return true;
        } catch (RestClientException e) {
            log.warn("Elasticsearch 문서 삭제 실패 groupId={} cause={}", groupId, e.getClass().getSimpleName());
            return false;
        }
    }

    /** 공개 모임 목록을 벌크 NDJSON 으로 일괄 색인한다. */
    public int bulkIndex(List<GroupSearchDocument> docs) {
        if (!enabled || docs == null || docs.isEmpty()) {
            return 0;
        }
        StringBuilder ndjson = new StringBuilder();
        int count = 0;
        for (GroupSearchDocument doc : docs) {
            if (doc == null || doc.groupId() == null) {
                continue;
            }
            ndjson.append("{\"index\":{\"_index\":\"").append(escapeJson(indexName))
                    .append("\",\"_id\":\"").append(doc.groupId()).append("\"}}\n");
            ndjson.append(toJsonLine(doc)).append('\n');
            count++;
        }
        if (count == 0) {
            return 0;
        }
        try {
            restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/_bulk")
                            .queryParam("refresh", "true")
                            .build())
                    .contentType(NDJSON)
                    .body(ndjson.toString())
                    .retrieve()
                    .body(JSON_OBJECT);
            return count;
        } catch (RestClientException e) {
            log.warn("Elasticsearch 벌크 색인 실패 count={} cause={}", count, e.getClass().getSimpleName());
            return 0;
        }
    }

    /**
     * F12 공개 모임 검색: {@code VISIBILITY=PUBLIC}, {@code STATUS!=DISSOLVED} 고정 + title(nori)/region/purpose 조건.
     *
     * @return ES 정상 응답 시 매칭된 {@code groupId} 목록(0건 포함), ES 비활성화 또는 장애 시 {@link Optional#empty()}
     */
    public Optional<List<Long>> searchPublicGroupIds(String region, String purpose, String q) {
        if (!enabled) {
            return Optional.empty();
        }
        Map<String, Object> requestBody = buildPublicSearchRequest(region, purpose, q);
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/{index}/_search", indexName)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(JSON_OBJECT);
            return Optional.of(extractHitIds(response));
        } catch (RestClientException e) {
            log.warn("Elasticsearch 공개 모임 검색 실패 — DB LIKE 폴백으로 전환합니다. cause={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * F11 모임 추천 검색: {@code VISIBILITY=PUBLIC}, {@code STATUS=RECRUITING}, {@code amount <= maxAmount}
     * 및 시/도 · 시/군/구 필터로 후보 {@code groupId} 목록을 조회한다.
     *
     * @return ES 정상 응답 시 매칭된 {@code groupId} 목록(0건 포함), ES 비활성화 또는 장애 시 {@link Optional#empty()}
     */
    public Optional<List<Long>> searchRecommendedGroupIds(
            long maxAmount,
            String regionSido,
            String regionSigungu
    ) {
        if (!enabled) {
            return Optional.empty();
        }
        Map<String, Object> requestBody = buildRecommendationSearchRequest(maxAmount, regionSido, regionSigungu);
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/{index}/_search", indexName)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(JSON_OBJECT);
            return Optional.of(extractHitIds(response));
        } catch (RestClientException e) {
            log.warn("Elasticsearch 추천 모임 검색 실패 — DB 폴백으로 전환합니다. cause={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    static Map<String, Object> buildRecommendationSearchRequest(
            long maxAmount,
            String regionSido,
            String regionSigungu
    ) {
        List<Object> filters = new ArrayList<>();
        filters.add(Map.of("term", Map.of("visibility", "PUBLIC")));
        filters.add(Map.of("term", Map.of("status", "RECRUITING")));
        filters.add(Map.of("range", Map.of("amount", Map.of("lte", maxAmount))));

        if (regionSido != null && !regionSido.isBlank()) {
            String cleanSido = regionSido.strip();
            List<Object> sidoShould = new ArrayList<>();
            sidoShould.add(Map.of("term", Map.of("regionSido", cleanSido)));
            sidoShould.add(Map.of("wildcard", Map.of("regionSido",
                    Map.of("value", "*" + escapeWildcard(cleanSido) + "*", "case_insensitive", true))));
            sidoShould.add(Map.of("match", Map.of("regionFull", Map.of("query", cleanSido, "operator", "and"))));
            Map<String, Object> sidoBool = new LinkedHashMap<>();
            sidoBool.put("should", sidoShould);
            sidoBool.put("minimum_should_match", 1);
            filters.add(Map.of("bool", sidoBool));
        }

        if (regionSigungu != null && !regionSigungu.isBlank()) {
            String cleanSigungu = regionSigungu.strip();
            List<Object> sigunguShould = new ArrayList<>();
            sigunguShould.add(Map.of("term", Map.of("regionSigungu", cleanSigungu)));
            sigunguShould.add(Map.of("wildcard", Map.of("regionSigungu",
                    Map.of("value", "*" + escapeWildcard(cleanSigungu) + "*", "case_insensitive", true))));
            sigunguShould.add(Map.of("match", Map.of("regionFull", Map.of("query", cleanSigungu, "operator", "and"))));
            Map<String, Object> sigunguBool = new LinkedHashMap<>();
            sigunguBool.put("should", sigunguShould);
            sigunguBool.put("minimum_should_match", 1);
            filters.add(Map.of("bool", sigunguBool));
        }

        Map<String, Object> boolQuery = new LinkedHashMap<>();
        boolQuery.put("filter", filters);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("size", SEARCH_SIZE);
        body.put("_source", false);
        body.put("query", Map.of("bool", boolQuery));
        body.put("sort", List.of(Map.of("groupId", Map.of("order", "desc"))));
        return body;
    }

    static Map<String, Object> buildPublicSearchRequest(String region, String purpose, String q) {
        List<Object> filters = new ArrayList<>();
        // 비공개 모임 0건 보장
        filters.add(Map.of("term", Map.of("visibility", "PUBLIC")));

        if (purpose != null && !purpose.isBlank()) {
            filters.add(Map.of("term", Map.of("purpose", purpose.strip())));
        }

        if (region != null && !region.isBlank()) {
            String cleanRegion = region.strip();
            String wildcardPattern = "*" + escapeWildcard(cleanRegion) + "*";
            List<Object> regionShould = new ArrayList<>();
            regionShould.add(Map.of("term", Map.of("regionSido", cleanRegion)));
            regionShould.add(Map.of("term", Map.of("regionSigungu", cleanRegion)));
            regionShould.add(Map.of("term", Map.of("regionDong", cleanRegion)));
            regionShould.add(Map.of("match", Map.of("regionFull", Map.of("query", cleanRegion, "operator", "and"))));
            regionShould.add(Map.of("wildcard", Map.of("regionSido", Map.of("value", wildcardPattern, "case_insensitive", true))));
            regionShould.add(Map.of("wildcard", Map.of("regionSigungu", Map.of("value", wildcardPattern, "case_insensitive", true))));
            regionShould.add(Map.of("wildcard", Map.of("regionDong", Map.of("value", wildcardPattern, "case_insensitive", true))));

            Map<String, Object> regionBool = new LinkedHashMap<>();
            regionBool.put("should", regionShould);
            regionBool.put("minimum_should_match", 1);
            filters.add(Map.of("bool", regionBool));
        }

        Map<String, Object> boolQuery = new LinkedHashMap<>();
        boolQuery.put("filter", filters);
        boolQuery.put("must_not", List.of(Map.of("term", Map.of("status", "DISSOLVED"))));

        boolean hasQuery = q != null && !q.isBlank();
        if (hasQuery) {
            String cleanQ = q.strip();
            List<Object> titleShould = new ArrayList<>();
            titleShould.add(Map.of("match", Map.of("title", Map.of("query", cleanQ, "operator", "and"))));
            titleShould.add(Map.of("match_phrase_prefix", Map.of("title", Map.of("query", cleanQ))));
            titleShould.add(Map.of("wildcard", Map.of("title.keyword",
                    Map.of("value", "*" + escapeWildcard(cleanQ) + "*", "case_insensitive", true))));

            Map<String, Object> titleBool = new LinkedHashMap<>();
            titleBool.put("should", titleShould);
            titleBool.put("minimum_should_match", 1);
            boolQuery.put("must", List.of(Map.of("bool", titleBool)));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("size", SEARCH_SIZE);
        body.put("_source", false);
        body.put("query", Map.of("bool", boolQuery));
        body.put("sort", hasQuery
                ? List.of("_score", Map.of("groupId", Map.of("order", "desc")))
                : List.of(Map.of("groupId", Map.of("order", "desc"))));
        return body;
    }

    private static List<Long> extractHitIds(Map<String, Object> response) {
        if (response == null) {
            return List.of();
        }
        Object hitsWrapper = response.get("hits");
        if (!(hitsWrapper instanceof Map<?, ?> wrapperMap)) {
            return List.of();
        }
        Object hitsObj = wrapperMap.get("hits");
        if (!(hitsObj instanceof List<?> hitList)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>(hitList.size());
        for (Object item : hitList) {
            if (!(item instanceof Map<?, ?> hit)) {
                continue;
            }
            Object rawId = hit.get("_id");
            if (rawId != null) {
                try {
                    ids.add(Long.valueOf(String.valueOf(rawId).strip()));
                } catch (NumberFormatException ignored) {
                    // skip non-numeric document ids
                }
            }
        }
        return ids;
    }

    private static String escapeWildcard(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("?", "\\?");
    }

    private static String toJsonLine(GroupSearchDocument doc) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        appendNumber(sb, "groupId", doc.groupId(), true);
        appendString(sb, "title", doc.title(), false);
        appendNumber(sb, "ownerId", doc.ownerId(), false);
        appendString(sb, "status", doc.status(), false);
        appendString(sb, "purpose", doc.purpose(), false);
        appendString(sb, "visibility", doc.visibility(), false);
        appendString(sb, "regionSido", doc.regionSido(), false);
        appendString(sb, "regionSigungu", doc.regionSigungu(), false);
        appendString(sb, "regionDong", doc.regionDong(), false);
        appendString(sb, "regionFull", doc.regionFull(), false);
        appendNumber(sb, "amount", doc.amount(), false);
        appendNumber(sb, "maxMembers", doc.maxMembers(), false);
        appendNumber(sb, "currentMembers", doc.currentMembers(), false);
        appendDecimal(sb, "lat", doc.lat(), false);
        appendDecimal(sb, "lng", doc.lng(), false);
        appendString(sb, "description", doc.description(), false);
        sb.append('}');
        return sb.toString();
    }

    private static void appendString(StringBuilder sb, String key, String value, boolean first) {
        if (!first) sb.append(',');
        sb.append('"').append(key).append("\":");
        if (value == null) {
            sb.append("null");
        } else {
            sb.append('"').append(escapeJson(value)).append('"');
        }
    }

    private static void appendNumber(StringBuilder sb, String key, Number value, boolean first) {
        if (!first) sb.append(',');
        sb.append('"').append(key).append("\":");
        sb.append(value == null ? "null" : value);
    }

    private static void appendDecimal(StringBuilder sb, String key, BigDecimal value, boolean first) {
        if (!first) sb.append(',');
        sb.append('"').append(key).append("\":");
        sb.append(value == null ? "null" : value.toPlainString());
    }

    private static String escapeJson(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
