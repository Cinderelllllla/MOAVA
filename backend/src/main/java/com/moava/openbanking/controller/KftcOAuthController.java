package com.moava.openbanking.controller;

import com.moava.openbanking.dto.KftcOAuthDto;
import com.moava.openbanking.service.KftcOAuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 금융결제원 OAuth 2.0 모의 인증 컨트롤러
 */
@RestController
@RequestMapping("/oauth/2.0")
@RequiredArgsConstructor
public class KftcOAuthController {

    private final KftcOAuthService oauthService;

    // 1. 인가 코드 발급 (POST JSON)
    @PostMapping("/authorize")
    public ResponseEntity<KftcOAuthDto.AuthorizeResponse> authorizePost(
            HttpServletRequest request,
            @RequestBody(required = false) KftcOAuthDto.AuthorizeRequest req) {
        return secureOk(oauthService.issueAuthorizationCode(req, resolveClientIp(request)));
    }

    // 1-2. 인가 코드 발급 (GET Query — KFTC 표준 브라우저 리다이렉트 파라미터 호환)
    @GetMapping("/authorize")
    public ResponseEntity<KftcOAuthDto.AuthorizeResponse> authorizeGet(
            HttpServletRequest request,
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("scope") String scope,
            @RequestParam("user_seq_no") String userSeqNo,
            @RequestParam(value = "state", required = false) String state) {
        KftcOAuthDto.AuthorizeRequest req = new KftcOAuthDto.AuthorizeRequest();
        req.setClientId(clientId);
        req.setRedirectUri(redirectUri);
        req.setScope(scope);
        req.setUserSeqNo(userSeqNo);
        req.setState(state);
        return secureOk(oauthService.issueAuthorizationCode(req, resolveClientIp(request)));
    }

    // 2. Access Token 발급 (authorization_code 또는 client_credentials + scope=oob)
    @PostMapping("/token")
    public ResponseEntity<KftcOAuthDto.TokenResponse> issueToken(
            HttpServletRequest request,
            @RequestBody(required = false) KftcOAuthDto.TokenRequest req) {
        return secureOk(oauthService.issueToken(req, resolveClientIp(request)));
    }

    // 3. Access Token 즉시 철회
    @PostMapping("/revoke")
    public ResponseEntity<KftcOAuthDto.RevokeResponse> revokeToken(
            HttpServletRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return secureOk(oauthService.revokeToken(authorization, resolveClientIp(request)));
    }

    private static String resolveClientIp(HttpServletRequest request) {
        if (request == null || request.getRemoteAddr() == null || request.getRemoteAddr().isBlank()) {
            return "unknown";
        }
        return request.getRemoteAddr();
    }

    private static <T> ResponseEntity<T> secureOk(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }
}
