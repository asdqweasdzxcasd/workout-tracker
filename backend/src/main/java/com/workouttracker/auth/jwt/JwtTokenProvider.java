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
 * <p>Access / Refresh 토큰의 시크릿을 <b>분리</b> 보관해 방어 심층화(defense-in-depth).
 * 둘 중 하나가 유출되어도 다른 한 쪽은 살아있고, 시크릿을 독립적으로 롤링할 수 있다.</p>
 *
 * <ul>
 *   <li>알고리즘: HS256</li>
 *   <li>Access: 짧은 만료 (기본 900초 = 15분). subject=userId, claim=email</li>
 *   <li>Refresh: 긴 만료 (기본 1209600초 = 14일). subject=userId, jti=회전 식별자</li>
 * </ul>
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private static final String CLAIM_EMAIL = "email";

    private final SecretKey accessSecretKey;
    private final long accessExpiresInSeconds;
    private final SecretKey refreshSecretKey;
    private final long refreshExpiresInSeconds;

    public JwtTokenProvider(
            @Value("${jwt.access.secret}") String accessSecret,
            @Value("${jwt.access.expires-in-seconds:900}") long accessExpiresInSeconds,
            @Value("${jwt.refresh.secret}") String refreshSecret,
            @Value("${jwt.refresh.expires-in-seconds:1209600}") long refreshExpiresInSeconds
    ) {
        this.accessSecretKey = buildKey(accessSecret, "jwt.access.secret");
        this.accessExpiresInSeconds = accessExpiresInSeconds;
        this.refreshSecretKey = buildKey(refreshSecret, "jwt.refresh.secret");
        this.refreshExpiresInSeconds = refreshExpiresInSeconds;
    }

    private static SecretKey buildKey(String secret, String name) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    name + " 은 최소 32바이트 이상이어야 합니다. 현재 길이=" + bytes.length);
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    /** Access Token 생성 (subject=userId, claim=email). */
    public String generateAccessToken(Long userId, String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessExpiresInSeconds * 1000L);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_EMAIL, email)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(accessSecretKey)
                .compact();
    }

    /**
     * Refresh Token 생성. subject=userId, jti=회전 식별자.
     * email 등 부가 클레임은 보관하지 않는다 (Refresh 는 신원 확인용이 아니라 회전/검증용).
     */
    public String generateRefreshToken(Long userId, String jti) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshExpiresInSeconds * 1000L);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .id(jti)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(refreshSecretKey)
                .compact();
    }

    /** Access Token 검증 + 클레임 추출. 실패 시 jjwt 의 RuntimeException 이 그대로 던져진다. */
    public Claims parseAccessClaims(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(accessSecretKey)
                .build()
                .parseSignedClaims(token);
        return jws.getPayload();
    }

    /** Refresh Token 검증 + 클레임 추출. 실패 시 jjwt 의 RuntimeException 이 그대로 던져진다. */
    public Claims parseRefreshClaims(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(refreshSecretKey)
                .build()
                .parseSignedClaims(token);
        return jws.getPayload();
    }

    /** subject 클레임에서 userId 추출. */
    public Long extractUserId(Claims claims) {
        return Long.parseLong(claims.getSubject());
    }

    /** email 클레임 추출 (Access Token 전용). */
    public String extractEmail(Claims claims) {
        return claims.get(CLAIM_EMAIL, String.class);
    }

    /** Access Token 만료 시간(초). */
    public long getAccessExpiresInSeconds() {
        return accessExpiresInSeconds;
    }

    /** Refresh Token 만료 시간(초). */
    public long getRefreshExpiresInSeconds() {
        return refreshExpiresInSeconds;
    }
}
