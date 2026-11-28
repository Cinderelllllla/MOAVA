package com.moava.group.search;

import com.moava.group.search.dto.GroupSearchDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** F12 Elasticsearch REST 클라이언트 단위 테스트 (실제 ES 서버 호출 없음) */
class GroupElasticsearchClientTest {

    MockRestServiceServer server;
    GroupElasticsearchClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GroupElasticsearchClient(builder, true, "http://es.test:9200", "moava_group");
    }

    private static GroupSearchDocument sampleDoc(Long groupId, String title) {
        return new GroupSearchDocument(
                groupId,
                title,
                1L,
                "RECRUITING",
                "SAVINGS",
                "PUBLIC",
                "서울특별시",
                "마포구",
                "합정동",
                "서울특별시 마포구 합정동",
                200_000L,
                6,
                2,
                new BigDecimal("37.5495072"),
                new BigDecimal("126.9139405"),
                "마포구 직장인 저축 모임"
        );
    }

    @Test
    @DisplayName("인덱스가 이미 존재하면 HEAD 200으로 종료한다")
    void ensureIndexReturnsTrueWhenExists() {
        server.expect(requestTo("http://es.test:9200/moava_group"))
                .andExpect(method(HttpMethod.HEAD))
                .andRespond(withStatus(HttpStatus.OK));

        assertTrue(client.ensureIndex());
        server.verify();
    }

    @Test
    @DisplayName("인덱스가 없으면(404) nori 분석기 매핑으로 PUT 생성한다")
    void ensureIndexCreatesNoriIndexOn404() {
        server.expect(requestTo("http://es.test:9200/moava_group"))
                .andExpect(method(HttpMethod.HEAD))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("http://es.test:9200/moava_group"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("nori_tokenizer")))
                .andExpect(content().string(containsString("moava_nori")))
                .andRespond(withSuccess("{\"acknowledged\":true}", MediaType.APPLICATION_JSON));

        assertTrue(client.ensureIndex());
        server.verify();
    }

    @Test
    @DisplayName("단건 문서 색인과 삭제(404 포함)를 정상 처리한다")
    void indexAndDeleteDocument() {
        server.expect(requestTo("http://es.test:9200/moava_group/_doc/101?refresh=true"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().string(containsString("마포 목돈 모임")))
                .andRespond(withSuccess("{\"result\":\"created\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("http://es.test:9200/moava_group/_doc/101?refresh=true"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertTrue(client.indexDocument(sampleDoc(101L, "마포 목돈 모임")));
        assertTrue(client.deleteDocument(101L));
        server.verify();
    }

    @Test
    @DisplayName("벌크 색인은 UTF-8 NDJSON 형식으로 전송하고 색인 건수를 반환한다")
    void bulkIndexSendsNdjson() {
        server.expect(requestTo("http://es.test:9200/_bulk?refresh=true"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType("application/x-ndjson;charset=UTF-8"))
                .andExpect(content().string(containsString("\"_index\":\"moava_group\"")))
                .andExpect(content().string(containsString("\"_id\":\"101\"")))
                .andExpect(content().string(containsString("합정동")))
                .andRespond(withSuccess("{\"errors\":false}", MediaType.APPLICATION_JSON));

        int count = client.bulkIndex(List.of(
                sampleDoc(101L, "마포 \"목돈\" 모임"),
                sampleDoc(102L, "서교동 재테크 모임")
        ));

        assertEquals(2, count);
        server.verify();
    }

    @Test
    @DisplayName("공개 모임 검색은 PUBLIC·RECRUITING 필터·nori 질의 후 히트 순서를 보존해 ID 목록을 반환한다")
    void searchPublicGroupIdsPreservesHitOrder() {
        server.expect(requestTo("http://es.test:9200/moava_group/_search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"visibility\":\"PUBLIC\"")))
                .andExpect(content().string(containsString("\"status\":\"RECRUITING\"")))
                .andExpect(content().string(containsString("\"purpose\":\"SAVINGS\"")))
                .andExpect(content().string(containsString("합정동")))
                .andExpect(content().string(containsString("목돈")))
                .andRespond(withSuccess(
                        "{\"hits\":{\"hits\":[{\"_id\":\"205\"},{\"_id\":\"101\"}]}}",
                        MediaType.APPLICATION_JSON
                ));

        Optional<List<Long>> result = client.searchPublicGroupIds("합정동", "SAVINGS", "목돈");

        assertTrue(result.isPresent());
        assertEquals(List.of(205L, 101L), result.get());
        server.verify();
    }

    @Test
    @DisplayName("ES 정상 응답이 0건이면 빈 리스트 Optional을 반환한다")
    void searchPublicGroupIdsReturnsEmptyListWhenZeroHits() {
        server.expect(requestTo("http://es.test:9200/moava_group/_search"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"hits\":{\"hits\":[]}}", MediaType.APPLICATION_JSON));

        Optional<List<Long>> result = client.searchPublicGroupIds(null, null, "없는모임");

        assertTrue(result.isPresent());
        assertTrue(result.get().isEmpty());
        server.verify();
    }

    @Test
    @DisplayName("ES 연결 실패나 5xx 장애 시 예외 대신 Optional.empty()를 반환해 폴백을 유도한다")
    void searchPublicGroupIdsReturnsEmptyOptionalOnFailure() {
        server.expect(requestTo("http://es.test:9200/moava_group/_search"))
                .andRespond(withException(new ConnectException("Connection refused")));

        assertTrue(client.searchPublicGroupIds("마포구", "SAVINGS", "목돈").isEmpty());
        server.verify();
    }

    @Test
    @DisplayName("ES가 비활성화되어 있으면 외부 HTTP 호출 없이 즉시 반환한다")
    void disabledClientDoesNotCallHttp() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer unusedServer = MockRestServiceServer.bindTo(builder).build();
        GroupElasticsearchClient disabled = new GroupElasticsearchClient(builder, false, "http://es.test:9200", "moava_group");

        assertFalse(disabled.isEnabled());
        assertFalse(disabled.ensureIndex());
        assertFalse(disabled.indexDocument(sampleDoc(1L, "테스트")));
        assertFalse(disabled.deleteDocument(1L));
        assertEquals(0, disabled.bulkIndex(List.of(sampleDoc(1L, "테스트"))));
        assertTrue(disabled.searchPublicGroupIds(null, null, "테스트").isEmpty());
        assertTrue(disabled.searchRecommendedGroupIds(300_000L, "서울특별시", "마포구").isEmpty());
        unusedServer.verify();
    }

    @Test
    @DisplayName("F11 추천 검색은 PUBLIC·RECRUITING·amount 상한·지역 조건으로 ES를 조회한다")
    void searchRecommendedGroupIdsBuildsRecruitingRangeQuery() {
        server.expect(requestTo("http://es.test:9200/moava_group/_search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"visibility\":\"PUBLIC\"")))
                .andExpect(content().string(containsString("\"status\":\"RECRUITING\"")))
                .andExpect(content().string(containsString("\"lte\":300000")))
                .andExpect(content().string(containsString("서울특별시")))
                .andExpect(content().string(containsString("마포구")))
                .andRespond(withSuccess(
                        "{\"hits\":{\"hits\":[{\"_id\":\"101\"},{\"_id\":\"102\"}]}}",
                        MediaType.APPLICATION_JSON
                ));

        Optional<List<Long>> result = client.searchRecommendedGroupIds(300_000L, "서울특별시", "마포구");

        assertTrue(result.isPresent());
        assertEquals(List.of(101L, 102L), result.get());
        server.verify();
    }
}
