package com.moava.group.search;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * F11/F12 — Elasticsearch {@code moava_group} 인덱스 설정 및 매핑 정의.
 * <ul>
 *   <li>한국어 형태소 분석기 {@code analysis-nori} 기반 {@code moava_nori} 애널라이저 사용</li>
 *   <li>{@code decompound_mode = mixed} 로 복합어("목돈마련" → "목돈", "마련", "목돈마련") 검색 지원</li>
 * </ul>
 */
public final class GroupSearchIndexDefinition {

    public static final String NORI_ANALYZER = "moava_nori";

    private GroupSearchIndexDefinition() {}

    public static Map<String, Object> indexBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("settings", settings());
        body.put("mappings", mappings());
        return body;
    }

    private static Map<String, Object> settings() {
        Map<String, Object> noriTokenizer = new LinkedHashMap<>();
        noriTokenizer.put("type", "nori_tokenizer");
        noriTokenizer.put("decompound_mode", "mixed");

        Map<String, Object> noriAnalyzer = new LinkedHashMap<>();
        noriAnalyzer.put("type", "custom");
        noriAnalyzer.put("tokenizer", "moava_nori_tokenizer");
        noriAnalyzer.put("filter", List.of("lowercase", "nori_readingform"));

        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("tokenizer", Map.of("moava_nori_tokenizer", noriTokenizer));
        analysis.put("analyzer", Map.of(NORI_ANALYZER, noriAnalyzer));

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("number_of_shards", 1);
        settings.put("number_of_replicas", 0);
        settings.put("analysis", analysis);
        return settings;
    }

    private static Map<String, Object> mappings() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("groupId", Map.of("type", "long"));
        props.put("title", noriTextWithKeyword());
        props.put("ownerId", Map.of("type", "long"));
        props.put("status", Map.of("type", "keyword"));
        props.put("purpose", Map.of("type", "keyword"));
        props.put("visibility", Map.of("type", "keyword"));
        props.put("regionSido", keywordWithNoriText());
        props.put("regionSigungu", keywordWithNoriText());
        props.put("regionDong", keywordWithNoriText());
        props.put("regionFull", noriTextWithKeyword());
        props.put("amount", Map.of("type", "long"));
        props.put("maxMembers", Map.of("type", "integer"));
        props.put("currentMembers", Map.of("type", "integer"));
        props.put("lat", Map.of("type", "double"));
        props.put("lng", Map.of("type", "double"));
        props.put("description", Map.of("type", "text", "analyzer", NORI_ANALYZER));

        return Map.of("properties", props);
    }

    private static Map<String, Object> noriTextWithKeyword() {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("type", "text");
        field.put("analyzer", NORI_ANALYZER);
        field.put("search_analyzer", NORI_ANALYZER);
        field.put("fields", Map.of("keyword", Map.of("type", "keyword")));
        return field;
    }

    private static Map<String, Object> keywordWithNoriText() {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("type", "keyword");
        field.put("fields", Map.of("text", Map.of("type", "text", "analyzer", NORI_ANALYZER)));
        return field;
    }
}
