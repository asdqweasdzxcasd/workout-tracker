package com.workouttracker.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 토큰 발급/검증 컴포넌트.
 *
 * <ul>
 *   <li>알고리즘: HS256</li>
 *   <li>시크릿: 환경변수 {@code JWT_SECRET} (32바이트 이상)</li>
 *   <li>Access Token 만료: {@code jwt.expires-in-seconds} (기본 3600초)</li>
 * </ul>
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private static final String CLAIM_EMAIL = "email";

    private final SecretKey secretKey;
    private final long expiresInSeconds;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expires-in-seconds:3600}") long expiresInSeconds
    ) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT secret 은 최소 32바이트 이상이어야 합니다. 현재 길이=" + secretBytes.length);
        }
        this.secretKey = Keys.hmacShaKeyFor(secretBytes);
        this.expiresInSeconds = expiresInSeconds;
    }

    /** 사용자 ID(subject) + 이메일 클레임으로 Access Token 생성. */
    public String generateToken(Long userId, String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiresInSeconds * 1000L);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_EMAIL, email)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    /** 토큰을 검증하고 클레임을 반환한다. 검증 실패 시 jjwt 의 RuntimeException 이 그대로 던져진다. */
    public Claims parseClaims(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token);
        return jws.getPayload();
    }

    /** subject 클레임에서 userId 추출. */
    public Long extractUserId(Claims claims) {
        return Long.parseLong(claims.getSubject());
    }

    /** email 클레임 추출. */
    public String extractEmail(Claims claims) {
        return claims.get(CLAIM_EMAIL, String.class);
    }

    /** 토큰 만료 시간(초) 외부 노출. */
    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }
}
