package com.moava.openbanking.codef;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * CODEF API 설정 프로퍼티.
 * 민감키(client-id, client-secret, public-key)는 application-secret.properties에서 주입되며,
 * 키가 없는 로컬/테스트 환경에서도 Spring 컨텍스트 기동은 정상 유지된다.
 */
@Getter
@Component
public class CodefProperties {

    private final String oauthUrl;
    private final String baseUrl;
    private final String clientId;
    private final String clientSecret;
    private final String publicKey;

    public CodefProperties(
            @Value("${moava.api.codef.oauth-url:https://oauth.codef.io}") String oauthUrl,
            @Value("${moava.api.codef.base-url:https://development.codef.io}") String baseUrl,
            @Value("${moava.api.codef.client-id:}") String clientId,
            @Value("${moava.api.codef.client-secret:}") String clientSecret,
            @Value("${moava.api.codef.public-key:}") String publicKey
    ) {
        this.oauthUrl = stripTrailingSlash(oauthUrl);
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
        this.publicKey = publicKey == null ? "" : publicKey.trim();
    }

    public void requireCredentialsConfigured() {
        if (clientId.isBlank() || clientSecret.isBlank() || publicKey.isBlank()) {
            throw CodefException.serviceUnavailable(
                    "CODEF_NOT_CONFIGURED",
                    "CODEF API 키(client-id, client-secret, public-key)가 application-secret.properties에 설정되어 있지 않습니다."
            );
        }
    }

    private static String stripTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
