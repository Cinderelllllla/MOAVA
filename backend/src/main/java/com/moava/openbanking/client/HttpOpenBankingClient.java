package com.moava.openbanking.client;

import com.moava.openbanking.dto.KftcAccountDto;
import com.moava.openbanking.dto.KftcBaseResponse;
import com.moava.openbanking.dto.KftcOAuthDto;
import com.moava.openbanking.dto.KftcTransactionDto;
import com.moava.openbanking.dto.KftcTransferDto;
import com.moava.openbanking.exception.KftcOpenBankingException;
import com.moava.openbanking.exception.KftcResponseCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * RestClient 기반 금융결제원 오픈뱅킹 v2.0 HTTP 클라이언트.
 * application.properties의 moava.openbanking.base-url만 교체하면
 * 모의은행 ↔ 금융결제원 테스트베드 ↔ 상용 서버로 무코드 전환이 가능하다.
 */
@Component
public class HttpOpenBankingClient implements OpenBankingClient {

    private final OpenBankingProperties properties;
    private final RestClient restClient;

    @Autowired
    public HttpOpenBankingClient(OpenBankingProperties properties) {
        this(properties, buildDefaultRestClient(properties));
    }

    HttpOpenBankingClient(OpenBankingProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    private static RestClient buildDefaultRestClient(OpenBankingProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public KftcOAuthDto.AuthorizeResponse issueAuthorizationCode(KftcOAuthDto.AuthorizeRequest req) {
        return execute(() -> restClient.post()
                .uri("/oauth/2.0/authorize")
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(KftcOAuthDto.AuthorizeResponse.class));
    }

    @Override
    public KftcOAuthDto.TokenResponse issueOobToken() {
        KftcOAuthDto.TokenRequest req = new KftcOAuthDto.TokenRequest();
        req.setGrantType("client_credentials");
        req.setClientId(properties.getClientId());
        req.setClientSecret(properties.getClientSecret());
        req.setScope("oob");
        return execute(() -> restClient.post()
                .uri("/oauth/2.0/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(KftcOAuthDto.TokenResponse.class));
    }

    @Override
    public KftcOAuthDto.TokenResponse issueUserToken(String code, String redirectUri) {
        KftcOAuthDto.TokenRequest req = new KftcOAuthDto.TokenRequest();
        req.setGrantType("authorization_code");
        req.setClientId(properties.getClientId());
        req.setClientSecret(properties.getClientSecret());
        req.setCode(code);
        req.setRedirectUri(redirectUri);
        return execute(() -> restClient.post()
                .uri("/oauth/2.0/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(KftcOAuthDto.TokenResponse.class));
    }

    @Override
    public KftcOAuthDto.RevokeResponse revokeToken(String accessToken) {
        return execute(() -> restClient.post()
                .uri("/oauth/2.0/revoke")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(KftcOAuthDto.RevokeResponse.class));
    }

    @Override
    public KftcAccountDto.RealNameResponse inquireRealName(String accessToken, KftcAccountDto.RealNameRequest req) {
        return execute(() -> restClient.post()
                .uri("/v2.0/inquiry/real_name")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(KftcAccountDto.RealNameResponse.class));
    }

    @Override
    public KftcAccountDto.BalanceResponse inquireBalance(String accessToken, String fintechUseNum, String bankTranId) {
        return execute(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2.0/account/balance/fin_num")
                        .queryParam("fintech_use_num", fintechUseNum)
                        .queryParamIfPresent("bank_tran_id", nonBlank(bankTranId))
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(KftcAccountDto.BalanceResponse.class));
    }

    @Override
    public KftcTransactionDto.TransactionListResponse inquireTransactionList(
            String accessToken,
            String fintechUseNum,
            String inquiryType,
            String fromDate,
            String toDate,
            String sortOrder,
            Integer limit,
            String bankTranId
    ) {
        return execute(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2.0/account/transaction_list/fin_num")
                        .queryParam("fintech_use_num", fintechUseNum)
                        .queryParamIfPresent("inquiry_type", nonBlank(inquiryType))
                        .queryParamIfPresent("from_date", nonBlank(fromDate))
                        .queryParamIfPresent("to_date", nonBlank(toDate))
                        .queryParamIfPresent("sort_order", nonBlank(sortOrder))
                        .queryParamIfPresent("limit", Optional.ofNullable(limit))
                        .queryParamIfPresent("bank_tran_id", nonBlank(bankTranId))
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(KftcTransactionDto.TransactionListResponse.class));
    }

    @Override
    public KftcTransferDto.TransferResponse withdrawTransfer(String accessToken, KftcTransferDto.WithdrawRequest req) {
        return execute(() -> restClient.post()
                .uri("/v2.0/transfer/withdraw/fin_num")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(KftcTransferDto.TransferResponse.class));
    }

    @Override
    public KftcTransferDto.TransferResponse depositTransfer(String accessToken, KftcTransferDto.DepositRequest req) {
        return execute(() -> restClient.post()
                .uri("/v2.0/transfer/deposit/fin_num")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(KftcTransferDto.TransferResponse.class));
    }

    private static String bearer(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return "";
        }
        String trimmed = accessToken.trim();
        return trimmed.regionMatches(true, 0, "Bearer ", 0, 7) ? trimmed : "Bearer " + trimmed;
    }

    private static Optional<String> nonBlank(String value) {
        return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value.trim());
    }

    private <T> T execute(Supplier<T> action) {
        try {
            return action.get();
        } catch (RestClientResponseException ex) {
            KftcBaseResponse errorBody = null;
            try {
                errorBody = ex.getResponseBodyAs(KftcBaseResponse.class);
            } catch (Exception ignored) {
                // 비표준 오류 본문은 기본 내부 오류 코드로 매핑
            }
            if (errorBody != null && errorBody.getRspCode() != null) {
                KftcResponseCode mapped = resolveCode(errorBody.getRspCode());
                String message = (errorBody.getRspMessage() != null && !errorBody.getRspMessage().isBlank())
                        ? errorBody.getRspMessage()
                        : mapped.getDefaultMessage();
                throw new KftcOpenBankingException(mapped, message);
            }
            throw new KftcOpenBankingException(
                    KftcResponseCode.INTERNAL_ERROR,
                    "오픈뱅킹 서버 응답 오류 (HTTP " + ex.getStatusCode().value() + ")"
            );
        } catch (RestClientException ex) {
            throw new KftcOpenBankingException(
                    KftcResponseCode.INTERNAL_ERROR,
                    "오픈뱅킹 서버 통신에 실패했습니다: " + ex.getMessage()
            );
        }
    }

    private static KftcResponseCode resolveCode(String rspCode) {
        for (KftcResponseCode candidate : KftcResponseCode.values()) {
            if (candidate.getCode().equals(rspCode)) {
                return candidate;
            }
        }
        return KftcResponseCode.INTERNAL_ERROR;
    }
}
