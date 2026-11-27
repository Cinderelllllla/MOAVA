package com.moava.member.service;

import java.util.Map;
import java.util.Locale;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class SocialAuthService {

    private final Environment env;
    private final RestClient restClient;

    public SocialAuthService(Environment env) {
        this.env = env;

        var factory =
            new org.springframework.http.client.SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);

        this.restClient = RestClient.builder()
            .requestFactory(factory)
            .build();
    }

    public record SocialUser(
        String provider,
        String providerUserId,
        String email,
        String name
    ) {}

    private record Provider(
        String clientId,
        String clientSecret,
        String redirectUri,
        String authorizeUrl,
        String tokenUrl,
        String userInfoUrl
    ) {}

    private Provider getProvider(String provider) {

        String p = provider.toLowerCase(Locale.ROOT);
        String prefix = "moava.oauth." + p;

        String id = env.getProperty(prefix + ".client-id", "");
        String secret = env.getProperty(prefix + ".client-secret", "");
        String redirect = env.getProperty(prefix + ".redirect-uri", "");

        if (id.isBlank() || secret.isBlank()
                || redirect.isBlank()
                || id.contains("★") || secret.contains("★")) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "소셜 로그인 설정을 확인해주세요."
            );
        }

        return switch (p) {
            case "kakao" -> new Provider(
                id, secret, redirect,
                "https://kauth.kakao.com/oauth/authorize",
                "https://kauth.kakao.com/oauth/token",
                "https://kapi.kakao.com/v2/user/me"
            );

            case "naver" -> new Provider(
                id, secret, redirect,
                "https://nid.naver.com/oauth2.0/authorize",
                "https://nid.naver.com/oauth2.0/token",
                "https://openapi.naver.com/v1/nid/me"
            );

            case "google" -> new Provider(
                id, secret, redirect,
                "https://accounts.google.com/o/oauth2/v2/auth",
                "https://oauth2.googleapis.com/token",
                "https://openidconnect.googleapis.com/v1/userinfo"
            );

            default -> throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "지원하지 않는 로그인입니다."
            );
        };
    }

    // 외부 로그인 페이지 주소 생성
    public String getAuthorizeUrl(
        String provider,
        String state,
        String challenge
    ) {
        Provider p = getProvider(provider);

        var uri = UriComponentsBuilder
            .fromUriString(p.authorizeUrl())
            .queryParam("client_id", p.clientId())
            .queryParam("redirect_uri", p.redirectUri())
            .queryParam("response_type", "code")
            .queryParam("state", state);

        if ("google".equalsIgnoreCase(provider)) {
            uri.queryParam("scope", "openid email profile")
                .queryParam("prompt", "select_account")
                .queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256");
        }

        return uri.build().encode().toUriString();
    }

    // 인가 코드로 Access Token 발급
    public String getAccessToken(
        String provider,
        String code,
        String state,
        String verifier
    ) {
        Provider p = getProvider(provider);

        var params = new LinkedMultiValueMap<String, String>();

        params.add("grant_type", "authorization_code");
        params.add("client_id", p.clientId());
        params.add("client_secret", p.clientSecret());
        params.add("redirect_uri", p.redirectUri());
        params.add("code", code);

        if ("naver".equalsIgnoreCase(provider)) {
            params.add("state", state);
        }

        if ("google".equalsIgnoreCase(provider)) {
            params.add("code_verifier", verifier);
        }

        try {
            Map<?, ?> result = restClient.post()
                .uri(p.tokenUrl())
                .contentType(
                    MediaType.APPLICATION_FORM_URLENCODED
                )
                .body(params)
                .retrieve()
                .body(Map.class);

            if (result == null ||
                !(result.get("access_token") instanceof String token)
                || token.isBlank()) {

                throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "토큰 발급에 실패했습니다."
                );
            }

            return token;

        } catch (RestClientException e) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "외부 인증 서버 연결에 실패했습니다."
            );
        }
    }

    // 소셜 회원정보 조회
    public SocialUser getUserInfo(
        String provider,
        String accessToken
    ) {
        Provider p = getProvider(provider);

        try {
            Map<?, ?> result = restClient.get()
                .uri(p.userInfoUrl())
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .body(Map.class);

            if (result == null) {
                throw invalid();
            }

            SocialUser user = switch (
                provider.toLowerCase(Locale.ROOT)
            ) {
                case "kakao" -> {
                    Map<?, ?> account =
                        toMap(result.get("kakao_account"));

                    Map<?, ?> profile =
                        toMap(account.get("profile"));

                    String email =
                        Boolean.TRUE.equals(account.get("is_email_verified"))
                        && Boolean.TRUE.equals(account.get("is_email_valid"))
                        ? value(account.get("email"))
                        : null;

                    yield new SocialUser(
                        "KAKAO",
                        value(result.get("id")),
                        email,
                        value(profile.get("nickname"))
                    );
                }

                case "naver" -> {
                    if (!"00".equals(result.get("resultcode"))) {
                        throw invalid();
                    }

                    Map<?, ?> account =
                        toMap(result.get("response"));

                    yield new SocialUser(
                        "NAVER",
                        value(account.get("id")),
                        value(account.get("email")),
                        value(account.get("name"))
                    );
                }

                case "google" -> new SocialUser(
                    "GOOGLE",
                    value(result.get("sub")),
                    Boolean.TRUE.equals(result.get("email_verified"))
                        ? value(result.get("email"))
                        : null,
                    value(result.get("name"))
                );

                default -> throw invalid();
            };

            if (user.providerUserId() == null
                || user.providerUserId().isBlank()
                || user.providerUserId().length() > 100) {
                throw invalid();
            }

            return user;

        } catch (RestClientException e) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "소셜 회원정보 조회에 실패했습니다."
            );
        }
    }

    private Map<?, ?> toMap(Object value) {
        return value instanceof Map<?, ?> map
            ? map : Map.of();
    }

    private String value(Object value) {
        return value == null ? null : value.toString();
    }

    private ResponseStatusException invalid() {
        return new ResponseStatusException(
            HttpStatus.UNAUTHORIZED,
            "유효하지 않은 소셜 인증 정보입니다."
        );
    }
}