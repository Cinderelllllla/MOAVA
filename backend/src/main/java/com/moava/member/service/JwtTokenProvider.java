package com.moava.member.service;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;


@Component 
public class JwtTokenProvider {
    private final String secret;
    private final long expireMinutes;

    public JwtTokenProvider(@Value ("${moava.jwt.secret}") String secret, @Value("${moava.jwt.expire-minutes:120}") long expireMinutes) {
        if(secret == null || secret.getBytes(StandardCharsets.UTF_8).length <32) {
            throw new IllegalArgumentException("moava.jwt.secret은 최소 32바이트 이상이어야 합니다. 현재 길이: " + (secret == null ? 0 : secret.getBytes(StandardCharsets.UTF_8).length));
        }
        this.secret = secret;
        this.expireMinutes = expireMinutes;
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createToken(Long memberId) {
        return createToken(memberId, "MEMBER");
    }

    public String createToken(Long subjectId, String role) {
        if (subjectId == null || !("MEMBER".equals(role) || "ADMIN".equals(role))) {
            throw new IllegalArgumentException("토큰 주체 정보를 확인해 주세요.");
        }
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expireMinutes * 60_000L);
        return Jwts.builder()
                .subject(String.valueOf(subjectId))
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key())
                .compact();
    }

    public String getId(String token) {
        Claims claims = Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
        return claims.getSubject();
    }

    public boolean isValid(String token) {
        try {
            getId(token);
            String role = getRole(token);
            return "MEMBER".equals(role) || "ADMIN".equals(role);
        } catch (Exception e) {
            return false;
        }
    }

    public String getRole(String token) {
        Claims claims = Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
        String role = claims.get("role", String.class);
        return role == null ? "MEMBER" : role;
    }
}
