package com.moava.openbanking.client;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 오픈뱅킹 연동 설정.
 * 실제 금융결제원 테스트베드/운영 서버로 전환 시 코드 수정 없이
 * application.properties의 moava.openbanking.base-url만 교체하면 된다.
 */
@Getter
@Component
public class OpenBankingProperties {

    public static final String DEFAULT_QUICK_OAUTH_REDIRECT_URI = "http://localhost:3000/openbanking/bank-auth";

    private final String baseUrl;
    private final String orgCode;
    private final String clientId;
    private final String clientSecret;
    private final String quickOAuthRedirectUri;

    @Autowired
    public OpenBankingProperties(
            @Value("${moava.openbanking.base-url:http://localhost:8080}") String baseUrl,
            @Value("${moava.openbanking.org-code:M202600001}") String orgCode,
            @Value("${moava.openbanking.client-id:moava-demo-client}") String clientId,
            @Value("${moava.openbanking.client-secret:moava-demo-secret}") String clientSecret,
            @Value("${moava.openbanking.quick-oauth-redirect-uri:http://localhost:3000/openbanking/bank-auth}") String quickOAuthRedirectUri
    ) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.orgCode = orgCode;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.quickOAuthRedirectUri = (quickOAuthRedirectUri == null || quickOAuthRedirectUri.isBlank())
                ? DEFAULT_QUICK_OAUTH_REDIRECT_URI
                : quickOAuthRedirectUri.trim();
    }

    /** 기존 단위 테스트 호환용 4-인자 생성자 */
    public OpenBankingProperties(String baseUrl, String orgCode, String clientId, String clientSecret) {
        this(baseUrl, orgCode, clientId, clientSecret, DEFAULT_QUICK_OAUTH_REDIRECT_URI);
    }
}
