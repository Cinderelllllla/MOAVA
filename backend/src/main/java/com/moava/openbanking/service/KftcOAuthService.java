package com.moava.openbanking.service;

import com.moava.openbanking.client.OpenBankingProperties;
import com.moava.openbanking.dto.KftcOAuthDto;
import com.moava.openbanking.dto.KftcRecordDto;
import com.moava.openbanking.exception.KftcOpenBankingException;
import com.moava.openbanking.exception.KftcResponseCode;
import com.moava.openbanking.mapper.KftcOpenBankingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 금융결제원 OAuth 2.0 모의 인증/토큰 서비스
 * - authorization_code (3-legged: login / inquiry / transfer)
 * - client_credentials (2-legged: scope=oob)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KftcOAuthService {

    private static final long AUTH_CODE_TTL_SECONDS = 180;          // 3분
    private static final long TOKEN_TTL_SECONDS = 90L * 24 * 3600;  // 90일 (금결원 표준)

    private final KftcOpenBankingMapper mapper;
    private final KftcSecurityGuard guard;
    private final OpenBankingProperties properties;

    @Transactional
    public KftcOAuthDto.AuthorizeResponse issueAuthorizationCode(
            KftcOAuthDto.AuthorizeRequest req,
            String clientIp
    ) {
        guard.checkRateLimit("OAUTH_AUTHZ", clientIp, 20);
        guard.checkNotBlocked(clientIp);

        if (req == null) {
            throw new KftcOpenBankingException(KftcResponseCode.INVALID_PARAMETER, "요청 본문이 비어 있습니다.");
        }
        if (!guard.constantTimeEquals(properties.getClientId(), req.getClientId())) {
            guard.recordAuthFailure(clientIp);
            throw new KftcOpenBankingException(KftcResponseCode.UNAUTHORIZED_TOKEN, "유효하지 않은 client_id입니다.");
        }
        String scope = guard.validateScopeString(req.getScope());
        String userSeqNo = guard.validateUserSeqNo(req.getUserSeqNo());
        String redirectUri = validateRedirectUri(req.getRedirectUri());

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        String code = guard.generateOpaqueSecret("kftc_code_");

        KftcRecordDto.OAuthCodeRecord record = new KftcRecordDto.OAuthCodeRecord();
        record.setAuthCode(code);
        record.setClientId(properties.getClientId());
        record.setUserSeqNo(userSeqNo);
        record.setScope(scope);
        record.setRedirectUri(redirectUri);
        record.setUsedYn("N");
        record.setCreatedAt(now);
        record.setExpiresAt(now.plusSeconds(AUTH_CODE_TTL_SECONDS));
        mapper.insertAuthCode(record);

        log.info("[KFTC-OAUTH] 인가코드 발급: userSeqNo={}, scope={}", userSeqNo, scope);

        KftcOAuthDto.AuthorizeResponse res = new KftcOAuthDto.AuthorizeResponse();
        res.fillSuccess();
        res.setCode(code);
        res.setState(req.getState());
        res.setScope(scope);
        res.setRedirectUri(redirectUri);
        res.setExpiresIn(AUTH_CODE_TTL_SECONDS);
        return res;
    }

    @Transactional
    public KftcOAuthDto.TokenResponse issueToken(
            KftcOAuthDto.TokenRequest req,
            String clientIp
    ) {
        guard.checkRateLimit("OAUTH_TOKEN", clientIp, 20);
        guard.checkNotBlocked(clientIp);

        if (req == null || req.getGrantType() == null || req.getGrantType().isBlank()) {
            throw new KftcOpenBankingException(KftcResponseCode.INVALID_PARAMETER, "grant_type은 필수입니다.");
        }
        boolean validClient = guard.constantTimeEquals(properties.getClientId(), req.getClientId())
                && guard.constantTimeEquals(properties.getClientSecret(), req.getClientSecret());
        if (!validClient) {
            guard.recordAuthFailure(clientIp);
            throw new KftcOpenBankingException(KftcResponseCode.UNAUTHORIZED_TOKEN, "client_id 또는 client_secret이 올바르지 않습니다.");
        }
        guard.clearAuthFailures(clientIp);

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        String grantType = req.getGrantType().trim();
        String grantedScope;
        String userSeqNo;

        if ("client_credentials".equals(grantType)) {
            grantedScope = guard.validateScopeString(req.getScope() != null ? req.getScope() : "oob");
            if (!"oob".equals(grantedScope)) {
                throw new KftcOpenBankingException(
                        KftcResponseCode.INVALID_PARAMETER,
                        "client_credentials 방식은 scope=oob만 허용됩니다."
                );
            }
            userSeqNo = null;
        } else if ("authorization_code".equals(grantType)) {
            if (req.getCode() == null || req.getCode().isBlank()) {
                throw new KftcOpenBankingException(KftcResponseCode.INVALID_PARAMETER, "인가 코드(code)는 필수입니다.");
            }
            String authCode = req.getCode().trim();
            KftcRecordDto.OAuthCodeRecord codeRecord = mapper.findAuthCode(authCode);
            if (codeRecord == null) {
                throw new KftcOpenBankingException(KftcResponseCode.UNAUTHORIZED_TOKEN, "유효하지 않은 인가 코드입니다.");
            }
            if (!properties.getClientId().equals(codeRecord.getClientId())) {
                throw new KftcOpenBankingException(KftcResponseCode.UNAUTHORIZED_TOKEN, "인가 코드의 client_id가 일치하지 않습니다.");
            }
            if (req.getRedirectUri() != null && !req.getRedirectUri().trim().equals(codeRecord.getRedirectUri())) {
                throw new KftcOpenBankingException(KftcResponseCode.INVALID_PARAMETER, "redirect_uri가 일치하지 않습니다.");
            }
            int consumed = mapper.consumeAuthCode(authCode, now);
            if (consumed != 1) {
                throw new KftcOpenBankingException(KftcResponseCode.UNAUTHORIZED_TOKEN, "만료되었거나 이미 사용된 인가 코드입니다.");
            }
            grantedScope = codeRecord.getScope();
            userSeqNo = codeRecord.getUserSeqNo();
        } else {
            throw new KftcOpenBankingException(
                    KftcResponseCode.INVALID_PARAMETER,
                    "지원하지 않는 grant_type입니다. (authorization_code 또는 client_credentials)"
            );
        }

        String rawAccessToken = guard.generateOpaqueSecret("kftc_tok_");
        String tokenHash = guard.sha256Hex(rawAccessToken);

        KftcRecordDto.OAuthTokenRecord tokenRecord = new KftcRecordDto.OAuthTokenRecord();
        tokenRecord.setTokenHash(tokenHash);
        tokenRecord.setClientId(properties.getClientId());
        tokenRecord.setUserSeqNo(userSeqNo);
        tokenRecord.setScope(grantedScope);
        tokenRecord.setRevokedYn("N");
        tokenRecord.setCreatedAt(now);
        tokenRecord.setExpiresAt(now.plusSeconds(TOKEN_TTL_SECONDS));
        mapper.insertOAuthToken(tokenRecord);

        log.info("[KFTC-OAUTH] Access Token 발급: grantType={}, userSeqNo={}, scope={}",
                grantType, userSeqNo, grantedScope);

        KftcOAuthDto.TokenResponse res = new KftcOAuthDto.TokenResponse();
        res.fillSuccess();
        res.setAccessToken(rawAccessToken);
        res.setTokenType("Bearer");
        res.setExpiresIn(TOKEN_TTL_SECONDS);
        res.setScope(grantedScope);
        res.setUserSeqNo(userSeqNo);
        return res;
    }

    public KftcRecordDto.OAuthTokenRecord validateBearerToken(
            String authorizationHeader,
            String requiredScope,
            String clientIp
    ) {
        guard.checkNotBlocked(clientIp);
        String rawToken = extractBearerToken(authorizationHeader, clientIp);
        String tokenHash = guard.sha256Hex(rawToken);

        KftcRecordDto.OAuthTokenRecord token = mapper.findOAuthTokenByHash(tokenHash);
        if (token == null || "Y".equals(token.getRevokedYn())
                || token.getExpiresAt() == null
                || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            guard.recordAuthFailure(clientIp);
            throw new KftcOpenBankingException(KftcResponseCode.UNAUTHORIZED_TOKEN);
        }
        guard.clearAuthFailures(clientIp);

        if (requiredScope != null && !requiredScope.isBlank()) {
            Set<String> granted = Arrays.stream(token.getScope().split("\\s+"))
                    .collect(Collectors.toSet());
            boolean allowed = granted.contains(requiredScope) || granted.contains("oob");
            if (!allowed) {
                throw new KftcOpenBankingException(
                        KftcResponseCode.INSUFFICIENT_SCOPE,
                        "필요한 권한(" + requiredScope + ")이 없는 토큰입니다."
                );
            }
        }
        return token;
    }

    @Transactional
    public KftcOAuthDto.RevokeResponse revokeToken(String authorizationHeader, String clientIp) {
        guard.checkRateLimit("OAUTH_REVOKE", clientIp, 30);
        String rawToken = extractBearerToken(authorizationHeader, clientIp);
        String tokenHash = guard.sha256Hex(rawToken);

        int updated = mapper.revokeOAuthToken(tokenHash);
        if (updated != 1) {
            throw new KftcOpenBankingException(KftcResponseCode.UNAUTHORIZED_TOKEN, "유효하지 않거나 이미 철회된 토큰입니다.");
        }
        log.info("[KFTC-OAUTH] Access Token 철회 완료");

        KftcOAuthDto.RevokeResponse res = new KftcOAuthDto.RevokeResponse();
        res.fillSuccess();
        res.setRevoked(true);
        return res;
    }

    private String extractBearerToken(String authorizationHeader, String clientIp) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            guard.recordAuthFailure(clientIp);
            throw new KftcOpenBankingException(
                    KftcResponseCode.UNAUTHORIZED_TOKEN,
                    "Authorization: Bearer <access_token> 헤더가 필요합니다."
            );
        }
        String token = authorizationHeader.substring(7).trim();
        if (token.isEmpty() || token.length() > 128) {
            guard.recordAuthFailure(clientIp);
            throw new KftcOpenBankingException(KftcResponseCode.UNAUTHORIZED_TOKEN);
        }
        return token;
    }

    private String validateRedirectUri(String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new KftcOpenBankingException(KftcResponseCode.INVALID_PARAMETER, "redirect_uri는 필수입니다.");
        }
        String trimmed = redirectUri.trim();
        if (trimmed.length() > 300 || (!trimmed.startsWith("http://") && !trimmed.startsWith("https://"))) {
            throw new KftcOpenBankingException(KftcResponseCode.INVALID_PARAMETER, "redirect_uri는 http:// 또는 https://로 시작해야 합니다.");
        }
        return trimmed;
    }
}
