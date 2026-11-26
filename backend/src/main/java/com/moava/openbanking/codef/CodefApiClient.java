package com.moava.openbanking.codef;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * CODEF REST API 저수준 HTTP 클라이언트:
 * - OAuth 2.0 토큰 발급 및 7일 메모리 캐싱 (데모 일 100건 한도 보호)
 * - 스크래핑 전용 타임아웃 (Connect 5초, Read 90초)
 * - CODEF URL-Encoded JSON 응답 디코딩 및 결과 코드(CF-*) 예외 매핑
 */
@Slf4j
@Component
public class CodefApiClient {

    private static final long TOKEN_REFRESH_MARGIN_SECONDS = 300L;

    private final CodefProperties properties;
    private final RestClient oauthClient;
    private final RestClient apiClient;
    private final ObjectMapper objectMapper;

    private volatile String cachedAccessToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    @Autowired
    public CodefApiClient(CodefProperties properties, ObjectMapper objectMapper) {
        this(
                properties,
                buildClient(properties.getOauthUrl(), Duration.ofSeconds(5), Duration.ofSeconds(15)),
                buildClient(properties.getBaseUrl(), Duration.ofSeconds(5), Duration.ofSeconds(90)),
                objectMapper
        );
    }

    CodefApiClient(CodefProperties properties, RestClient oauthClient, RestClient apiClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.oauthClient = oauthClient;
        this.apiClient = apiClient;
        this.objectMapper = objectMapper;
    }

    private static RestClient buildClient(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    /**
     * OAuth 2.0 Access Token 획득 (유효기간 내 캐시 재사용)
     */
    public String getAccessToken() {
        properties.requireCredentialsConfigured();
        Instant now = Instant.now();
        if (cachedAccessToken != null && now.isBefore(tokenExpiresAt)) {
            return cachedAccessToken;
        }
        synchronized (this) {
            now = Instant.now();
            if (cachedAccessToken != null && now.isBefore(tokenExpiresAt)) {
                return cachedAccessToken;
            }
            String basic = Base64.getEncoder().encodeToString(
                    (properties.getClientId() + ":" + properties.getClientSecret()).getBytes(StandardCharsets.UTF_8)
            );
            try {
                String raw = oauthClient.post()
                        .uri("/oauth/token")
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body("grant_type=client_credentials&scope=read")
                        .retrieve()
                        .body(String.class);

                JsonNode root = parseJsonLenient(raw);
                String token = root.path("access_token").asString("");
                long expiresIn = root.path("expires_in").asLong(3600L);
                if (token.isBlank()) {
                    throw CodefException.badGateway("CODEF_OAUTH_EMPTY", "CODEF OAuth 액세스 토큰 발급 응답이 비어 있습니다.");
                }
                long ttl = Math.max(60L, expiresIn - TOKEN_REFRESH_MARGIN_SECONDS);
                cachedAccessToken = token;
                tokenExpiresAt = Instant.now().plusSeconds(ttl);
                log.info("[CODEF] OAuth Access Token 발급 완료 (캐시 TTL={}초)", ttl);
                return token;
            } catch (CodefException e) {
                throw e;
            } catch (RestClientResponseException e) {
                String detail = extractSafeErrorDetail(e.getResponseBodyAsString());
                log.warn("[CODEF] OAuth 토큰 발급 실패: status={}, detail={}", e.getStatusCode().value(), detail);
                throw CodefException.badGateway("CODEF_OAUTH_FAILED", "CODEF 인증 토큰 발급에 실패했습니다. 키 설정을 확인해 주세요.");
            } catch (RestClientException e) {
                throw CodefException.badGateway("CODEF_OAUTH_NETWORK", "CODEF 인증 서버 통신에 실패했습니다.");
            }
        }
    }

    /**
     * CODEF API POST 호출 (Bearer 토큰 자동 부착 + 401/CF-00401 1회 자동 재발급 재시도 + URL-Decode + CF-* 결과코드 검증)
     */
    public JsonNode postApi(String path, Map<String, Object> payload) {
        return postApiInternal(path, payload, true);
    }

    private JsonNode postApiInternal(String path, Map<String, Object> payload, boolean allowRetryOnTokenError) {
        String accessToken = getAccessToken();
        try {
            String raw = apiClient.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            JsonNode root = parseJsonLenient(raw);
            String code = root.path("result").path("code").asString("");
            if ("CF-00401".equals(code) && allowRetryOnTokenError) {
                log.info("[CODEF] 액세스 토큰 만료 응답(CF-00401) 감지 — 토큰 재발급 후 1회 재시도: path={}", path);
                invalidateTokenCache();
                return postApiInternal(path, payload, false);
            }
            validateCodefResult(root);
            return root.path("data");
        } catch (CodefException e) {
            throw e;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            JsonNode errorRoot = tryParseJsonLenient(e.getResponseBodyAsString());
            String codefCode = errorRoot != null ? errorRoot.path("result").path("code").asString("") : "";

            if (status == 401 || "CF-00401".equals(codefCode)) {
                invalidateTokenCache();
                if (allowRetryOnTokenError) {
                    log.info("[CODEF] HTTP {}/{} 응답 감지 — 토큰 재발급 후 1회 재시도: path={}", status, codefCode, path);
                    return postApiInternal(path, payload, false);
                }
            }

            if (errorRoot != null && !codefCode.isBlank() && !"CF-00000".equals(codefCode)) {
                String codefMsg = sanitizeLogValue(errorRoot.path("result").path("message").asString(""));
                log.warn("[CODEF] API 호출 HTTP 오류: path={}, status={}, code={}, message={}",
                        path, status, codefCode, codefMsg);
                validateCodefResult(errorRoot);
            }

            String detail = extractSafeErrorDetail(errorRoot, e.getResponseBodyAsString());
            log.warn("[CODEF] API 호출 HTTP 오류: path={}, status={}, detail={}", path, status, detail);
            throw CodefException.badGateway("CODEF_HTTP_" + status,
                    "CODEF 서버 응답 오류가 발생했습니다. (HTTP " + status + ")");
        } catch (RestClientException e) {
            log.warn("[CODEF] API 통신 오류: path={}, msg={}", path, e.getMessage());
            throw CodefException.badGateway("CODEF_NETWORK_ERROR", "CODEF 서버 통신 중 시간 초과 또는 네트워크 오류가 발생했습니다.");
        }
    }

    public void invalidateTokenCache() {
        cachedAccessToken = null;
        tokenExpiresAt = Instant.EPOCH;
    }

    JsonNode parseJsonLenient(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            throw CodefException.badGateway("CODEF_EMPTY_RESPONSE", "CODEF 응답 본문이 비어 있습니다.");
        }
        String trimmed = rawBody.trim();
        String decoded = trimmed.startsWith("{") || trimmed.startsWith("[")
                ? trimmed
                : URLDecoder.decode(trimmed, StandardCharsets.UTF_8);
        try {
            return objectMapper.readTree(decoded);
        } catch (Exception e) {
            throw CodefException.badGateway("CODEF_PARSE_ERROR", "CODEF 응답 JSON 파싱에 실패했습니다.");
        }
    }

    private JsonNode tryParseJsonLenient(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        try {
            return parseJsonLenient(rawBody);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String extractSafeErrorDetail(String rawBody) {
        return extractSafeErrorDetail(tryParseJsonLenient(rawBody), rawBody);
    }

    private String extractSafeErrorDetail(JsonNode parsed, String rawBody) {
        if (parsed != null) {
            String resultCode = parsed.path("result").path("code").asString("");
            String resultMsg = parsed.path("result").path("message").asString("");
            if (!resultCode.isBlank() || !resultMsg.isBlank()) {
                return sanitizeLogValue((resultCode + " " + resultMsg).trim());
            }
            String err = parsed.path("error").asString("");
            String errDesc = parsed.path("error_description").asString("");
            if (!err.isBlank() || !errDesc.isBlank()) {
                return sanitizeLogValue((err + (errDesc.isBlank() ? "" : ": " + errDesc)).trim());
            }
            String msg = parsed.path("message").asString("");
            if (!msg.isBlank()) {
                return sanitizeLogValue(msg);
            }
        }
        if (rawBody == null || rawBody.isBlank()) {
            return "empty_body";
        }
        return "unparsed_body(len=" + rawBody.length() + ")";
    }

    private static String sanitizeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String cleaned = value.replaceAll("[\\r\\n\\t\\x00-\\x1F\\x7F]+", " ").trim();
        return cleaned.length() > 160 ? cleaned.substring(0, 160) + "..." : cleaned;
    }

    private void validateCodefResult(JsonNode root) {
        JsonNode result = root.path("result");
        String code = result.path("code").asString("");
        String message = result.path("message").asString("CODEF 처리 중 오류가 발생했습니다.");
        String extra = result.path("extraMessage").asString("");

        if ("CF-00000".equals(code)) {
            return;
        }
        if ("CF-03002".equals(code)) {
            throw CodefException.badRequest(
                    "CF-03002",
                    "은행 추가인증(2-Way)이 필요한 계정입니다. 공동인증서 또는 추가인증이 없는 계좌로 시도해 주세요."
            );
        }
        if ("CF-00401".equals(code) || "CF-00403".equals(code)) {
            invalidateTokenCache();
            // 프론트 apiFetch의 401 자동 로그아웃(MOAVA JWT 만료)과 혼동되지 않도록 502로 매핑한다.
            throw CodefException.badGateway(code, "CODEF 인증 토큰 또는 접근 권한 오류: " + message);
        }
        String fullMsg = extra.isBlank() ? message : message + " (" + extra + ")";
        throw CodefException.badRequest(code.isBlank() ? "CODEF_API_ERROR" : code, fullMsg);
    }
}
