package com.moava.openbanking.client;

import lombok.Getter;
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

    private final String baseUrl;
    private final String orgCode;
    private final String clientId;
    private final String clientSecret;

    public OpenBankingProperties(
            @Value("${moava.openbanking.base-url:http://localhost:8081}") String baseUrl,
            @Value("${moava.openbanking.org-code:M202600001}") String orgCode,
            @Value("${moava.openbanking.client-id:moava-demo-client}") String clientId,
            @Value("${moava.openbanking.client-secret:moava-demo-secret}") String clientSecret
    ) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.orgCode = orgCode;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }
}
