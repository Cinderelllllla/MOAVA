package com.moava.finance;

import com.moava.finance.service.FinanceException;
import com.moava.finance.service.MlClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** 가짜 ML 서버로 공유 키 헤더·오류 변환 확인 (실제 ML 서버 불필요) */
class MlClientTest {

    MockRestServiceServer server;
    MlClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MlClient(builder, "http://ml.test", "test-key");
    }

    private FinanceException fails(Runnable call) {
        return assertThrows(FinanceException.class, call::run);
    }

    @Test
    void 모든_요청에_공유_키_헤더를_붙인다() {
        server.expect(requestTo("http://ml.test/risk/model"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Key", "test-key"))
                .andRespond(withSuccess("{\"round_unit\":50000}", MediaType.APPLICATION_JSON));

        assertEquals(50000, client.model().get("round_unit"));
        server.verify();
    }

    @Test
    void ML_이_꺼져_있으면_503() {
        server.expect(requestTo("http://ml.test/risk/model")).andRespond(withException(new ConnectException("refused")));
        FinanceException e = fails(client::model);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
        assertEquals("ML_UNAVAILABLE", e.getCode());
    }

    @Test
    void 응답이_늦으면_503() {
        server.expect(requestTo("http://ml.test/risk/model")).andRespond(withException(new SocketTimeoutException("timeout")));
        assertEquals("ML_UNAVAILABLE", fails(client::model).getCode());
    }

    @Test
    void 공유_키가_틀려서_401이면_사용자에게는_503() {
        server.expect(requestTo("http://ml.test/risk/model")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, fails(client::model).getStatus());
    }

    @Test
    void ML_4xx_의_한국어_detail_은_그대로_400() {
        server.expect(requestTo("http://ml.test/risk/payout-order"))
                .andRespond(withStatus(HttpStatusCode.valueOf(404)).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"persona_id 'X' 를 찾을 수 없습니다.\"}"));
        FinanceException e = fails(() -> client.payoutOrder(java.util.List.of(1L, 2L)));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertEquals("persona_id 'X' 를 찾을 수 없습니다.", e.getMessage());
    }

    @Test
    void ML_4xx_의_영어_검증_오류는_기본_안내로_400() {
        server.expect(requestTo("http://ml.test/risk/contribution"))
                .andRespond(withStatus(HttpStatusCode.valueOf(422)).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":[{\"msg\":\"Input should be greater than or equal to 0\"}]}"));
        FinanceException e = fails(() -> client.contribution(Map.of("member_id", 1)));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertEquals("입력한 금융정보를 확인해 주세요.", e.getMessage());
    }

    @Test
    void ML_5xx_는_503() {
        server.expect(requestTo("http://ml.test/risk/model")).andRespond(withServerError());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, fails(client::model).getStatus());
    }
}
