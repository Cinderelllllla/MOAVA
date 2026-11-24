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

    public String createToken(Long id, String role) {
        if(id == null || id <= 0){
            throw new IllegalArgumentException("올바르지 않은 회원 번호입니다.");
        }
        if(!"MEMBER".equals(role) && !"ADMIN".equals(role)){
            throw new IllegalArgumentException("잘못된 사용자 권한입니다.");
        }
        // JWT 토큰 생성 로직 구현
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expireMinutes * 60_000L);
        return Jwts.builder()
            .subject(String.valueOf(id))
            .claim("role", role).
            issuedAt(now).
            expiration(expiry).
            signWith(key()).
            compact();
    }

    public String getId(String token) {
        Claims claims = getClaims(token);
        return claims.getSubject();
    }

    public boolean isValid(String token) {
        try {
            Claims claims = getClaims(token);
            String role = claims.get("role", String.class);
            return claims.getSubject() != null && ("MEMBER".equals(role) || "ADMIN".equals(role));
        } catch (Exception e) {
            return false;
        }
    }

    //사용자 권한
    public String getRole(String token){
        return getClaims(token).get("role", String.class);
    }

    //토큰 전체 정보
    private Claims getClaims(String token){
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
    }
}
