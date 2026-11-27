package com.moava.group.map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Optional;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Kakao Local 지오코딩 클라이언트 단위 테스트 (실제 Kakao 서버 호출 없음) */
class KakaoGeocodingClientTest {

    MockRestServiceServer server;
    KakaoGeocodingClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoGeocodingClient(builder, "https://kakao.test", "test-rest-key");
    }

    @Test
    void 주소_검색_응답에서_y는_위도_x는_경도로_변환하고_KakaoAK_헤더를_붙인다() {
        server.expect(requestTo(startsWith("https://kakao.test/v2/local/search/address.json")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "KakaoAK test-rest-key"))
                .andExpect(queryParam("size", "1"))
                .andRespond(withSuccess(
                        "{\"documents\":[{\"x\":\"126.9018489456\",\"y\":\"37.5663245123\"}]}",
                        MediaType.APPLICATION_JSON
                ));

        Optional<GeoPoint> result = client.geocodeRegion("서울특별시", "마포구");

        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("37.5663245"), result.get().lat());
        assertEquals(new BigDecimal("126.9018489"), result.get().lng());
        server.verify();
    }

    @Test
    void 주소_검색_결과가_비어_있으면_키워드_검색으로_폴백한다() {
        server.expect(requestTo(startsWith("https://kakao.test/v2/local/search/address.json")))
                .andRespond(withSuccess("{\"documents\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith("https://kakao.test/v2/local/search/keyword.json")))
                .andExpect(header("Authorization", "KakaoAK test-rest-key"))
                .andRespond(withSuccess(
                        "{\"documents\":[{\"x\":\"127.027621\",\"y\":\"37.497942\"}]}",
                        MediaType.APPLICATION_JSON
                ));

        Optional<GeoPoint> result = client.geocodeRegion("서울", "강남");

        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("37.4979420"), result.get().lat());
        assertEquals(new BigDecimal("127.0276210"), result.get().lng());
        server.verify();
    }

    @Test
    void 카카오_API_오류나_타임아웃이면_예외_대신_빈_Optional을_반환한다() {
        server.expect(requestTo(startsWith("https://kakao.test/v2/local/search/address.json")))
                .andRespond(withException(new SocketTimeoutException("timeout")));
        server.expect(requestTo(startsWith("https://kakao.test/v2/local/search/keyword.json")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertTrue(client.geocodeRegion("서울특별시", "성동구").isEmpty());
        server.verify();
    }

    @Test
    void 연결_거부_시에도_빈_Optional을_반환한다() {
        server.expect(requestTo(startsWith("https://kakao.test/v2/local/search/address.json")))
                .andRespond(withException(new ConnectException("refused")));
        server.expect(requestTo(startsWith("https://kakao.test/v2/local/search/keyword.json")))
                .andRespond(withServerError());

        assertTrue(client.geocodeRegion("부산광역시", "해운대구").isEmpty());
        server.verify();
    }

    @Test
    void REST_키가_비어_있으면_외부_호출을_하지_않는다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer unusedServer = MockRestServiceServer.bindTo(builder).build();
        KakaoGeocodingClient disabledClient = new KakaoGeocodingClient(builder, "https://kakao.test", "   ");

        assertTrue(disabledClient.geocodeRegion("서울특별시", "마포구").isEmpty());
        unusedServer.verify();
    }

    @Test
    void 읍면동이_있으면_시도_시군구_읍면동을_공백으로_조합해_질의한다() {
        server.expect(requestTo(startsWith("https://kakao.test/v2/local/search/address.json")))
                .andExpect(request -> {
                    String rawQuery = org.springframework.web.util.UriComponentsBuilder
                            .fromUri(request.getURI()).build().getQueryParams().getFirst("query");
                    String decoded = java.net.URLDecoder.decode(rawQuery, java.nio.charset.StandardCharsets.UTF_8);
                    assertEquals("경기도 고양시 일산동구 장항동", decoded);
                })
                .andRespond(withSuccess(
                        "{\"documents\":[{\"x\":\"126.7745123\",\"y\":\"37.6583654\"}]}",
                        MediaType.APPLICATION_JSON
                ));

        Optional<GeoPoint> result = client.geocodeRegion("경기도", "고양시 일산동구", "장항동");

        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("37.6583654"), result.get().lat());
        assertEquals(new BigDecimal("126.7745123"), result.get().lng());
        server.verify();
    }

    @Test
    void 읍면동이_null이거나_공백이면_시도와_시군구만으로_질의한다() {
        server.expect(requestTo(startsWith("https://kakao.test/v2/local/search/address.json")))
                .andExpect(request -> {
                    String rawQuery = org.springframework.web.util.UriComponentsBuilder
                            .fromUri(request.getURI()).build().getQueryParams().getFirst("query");
                    String decoded = java.net.URLDecoder.decode(rawQuery, java.nio.charset.StandardCharsets.UTF_8);
                    assertEquals("서울특별시 마포구", decoded);
                })
                .andRespond(withSuccess(
                        "{\"documents\":[{\"x\":\"126.9018489\",\"y\":\"37.5663245\"}]}",
                        MediaType.APPLICATION_JSON
                ));

        Optional<GeoPoint> result = client.geocodeRegion("서울특별시", "마포구", "   ");

        assertTrue(result.isPresent());
        server.verify();
    }
}
