package com.workouttracker.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtTokenProvider 단위 테스트.
 *
 * <p>핵심 보장:</p>
 * <ul>
 *   <li>Access / Refresh 토큰의 round-trip (발급 → 파싱) 이 클레임을 보존한다</li>
 *   <li>두 토큰의 시크릿이 분리되어 있어 서로의 parser 로 검증할 수 없다 (signature mismatch)</li>
 *   <li>만료된 토큰은 ExpiredJwtException 으로 거부된다</li>
 * </ul>
 */
@DisplayName("JwtTokenProvider 단위 테스트")
class JwtTokenProviderTest {

    private static final String ACCESS_SECRET = "test-access-secret-at-least-32-characters-long-for-hs256-OK";
    private static final String REFRESH_SECRET = "test-refresh-secret-at-least-32-characters-long-for-hs256-OK";

    private final JwtTokenProvider provider = new JwtTokenProvider(
            ACCESS_SECRET, 900L, REFRESH_SECRET, 1209600L
    );

    @Test
    @DisplayName("Access Token round-trip — userId / email 클레임이 보존된다")
    void accessToken_roundTrip_preservesClaims() {
        String token = provider.generateAccessToken(42L, "kim@example.com");

        Claims claims = provider.parseAccessClaims(token);

        assertThat(provider.extractUserId(claims)).isEqualTo(42L);
        assertThat(provider.extractEmail(claims)).isEqualTo("kim@example.com");
    }

    @Test
    @DisplayName("Refresh Token round-trip — userId / jti 클레임이 보존된다")
    void refreshToken_roundTrip_preservesClaims() {
        String jti = UUID.randomUUID().toString();
        String token = provider.generateRefreshToken(42L, jti);

        Claims claims = provider.parseRefreshClaims(token);

        assertThat(provider.extractUserId(claims)).isEqualTo(42L);
        assertThat(claims.getId()).isEqualTo(jti);
    }

    @Test
    @DisplayName("시크릿 분리 검증 — Access 토큰을 Refresh parser 로 검증하면 시그니처 불일치")
    void accessToken_parsedAsRefresh_throwsSignatureException() {
        String accessToken = provider.generateAccessToken(1L, "kim@example.com");

        assertThatThrownBy(() -> provider.parseRefreshClaims(accessToken))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    @DisplayName("시크릿 분리 검증 — Refresh 토큰을 Access parser 로 검증하면 시그니처 불일치")
    void refreshToken_parsedAsAccess_throwsSignatureException() {
        String refreshToken = provider.generateRefreshToken(1L, UUID.randomUUID().toString());

        assertThatThrownBy(() -> provider.parseAccessClaims(refreshToken))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    @DisplayName("만료된 Access Token 은 ExpiredJwtException 으로 거부")
    void expiredAccessToken_throwsExpiredException() throws InterruptedException {
        JwtTokenProvider shortLived = new JwtTokenProvider(
                ACCESS_SECRET, 1L, REFRESH_SECRET, 1209600L
        );
        String token = shortLived.generateAccessToken(1L, "kim@example.com");

        Thread.sleep(1100); // 1초 만료 + 약간의 여유

        assertThatThrownBy(() -> shortLived.parseAccessClaims(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("32바이트 미만 시크릿은 부팅 시 IllegalStateException")
    void shortSecret_failsAtConstruction() {
        assertThatThrownBy(() -> new JwtTokenProvider(
                "too-short", 900L, REFRESH_SECRET, 1209600L
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.access.secret");

        assertThatThrownBy(() -> new JwtTokenProvider(
                ACCESS_SECRET, 900L, "too-short", 1209600L
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.refresh.secret");
    }
}
