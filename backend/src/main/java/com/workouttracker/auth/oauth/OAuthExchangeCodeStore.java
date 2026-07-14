package com.workouttracker.auth.oauth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * 1회용 exchange code 저장소 (설계 3: 자체 토큰을 URL 에 싣지 않기).
 *
 * <p>소셜 로그인 성공 → 짧은 TTL 의 랜덤 code 만 프론트로 리다이렉트하고,
 * 프론트가 이 code 를 POST 로 실제 Access/Refresh 토큰과 교환한다.
 * 교환은 원자적 GET+DEL 이라 정확히 1회만 성공(재사용 즉시 실패).
 */
@Component
@RequiredArgsConstructor
public class OAuthExchangeCodeStore {

    private static final String KEY_PREFIX = "oauth2:exchange:";
    private static final Duration TTL = Duration.ofSeconds(60);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;

    /** 사용자 ID 를 담은 1회용 code 발급 (TTL 60초). */
    public String issue(Long userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redisTemplate.opsForValue().set(KEY_PREFIX + code, String.valueOf(userId), TTL);
        return code;
    }

    /** code 소비 — 성공 시 userId 반환 + 즉시 폐기. 만료/재사용이면 empty. */
    public Optional<Long> consume(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String userId = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + code);
        return Optional.ofNullable(userId).map(Long::valueOf);
    }
}
