package com.workouttracker.auth.token;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Redis 기반 {@link RefreshTokenStore} 구현.
 *
 * <h3>키 구조</h3>
 * <pre>
 *   rt:{userId}     SET     멤버 = 활성 jti 들 (다중 기기 표현)
 *   rt:meta:{jti}   STRING  값 = userId, TTL = refresh 만료
 * </pre>
 *
 * <p>SET 으로 다중 기기를 표현해 <code>SISMEMBER</code> 가 O(1) 이고 logout-all 일괄 삭제도
 * 깔끔하다. <code>rt:meta:{jti}</code> 의 TTL 이 jti 자연 만료를 보장하고, 동시에 jti → userId
 * 역인덱스 역할(운영 디버그 용)을 한다.</p>
 *
 * <h3>주의</h3>
 * <ul>
 *   <li>rt:{userId} SET 자체에는 TTL 을 두지 않는다 — 멤버가 0개면 의미상 비어있는 것과 같다.</li>
 *   <li>jti 만료(TTL on rt:meta:{jti})로 인해 rt:{userId} 의 멤버는 점차 "유령 jti" 가 될 수
 *       있지만, {@link #exists(Long, String)} 는 SET 멤버십을 기준으로 검증하므로 보안적으로 안전.
 *       유령 정리는 운영상 비용이 없을 때 별도 청소 잡(미구현)으로 회수 가능.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String USER_KEY_PREFIX = "rt:";
    private static final String META_KEY_PREFIX = "rt:meta:";

    private final StringRedisTemplate redis;

    @Override
    public void save(Long userId, String jti, long ttlSeconds) {
        redis.opsForSet().add(userKey(userId), jti);
        redis.opsForValue().set(metaKey(jti), String.valueOf(userId), Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public boolean exists(Long userId, String jti) {
        Boolean inSet = redis.opsForSet().isMember(userKey(userId), jti);
        return Boolean.TRUE.equals(inSet);
    }

    @Override
    public void delete(Long userId, String jti) {
        redis.opsForSet().remove(userKey(userId), jti);
        redis.delete(metaKey(jti));
    }

    @Override
    public void deleteAllByUser(Long userId) {
        String userKey = userKey(userId);
        Set<String> jtis = redis.opsForSet().members(userKey);
        if (jtis != null && !jtis.isEmpty()) {
            List<String> metaKeys = jtis.stream().map(this::metaKey).toList();
            redis.delete(metaKeys);
        }
        redis.delete(userKey);
    }

    @Override
    public Set<String> findAllJtiByUser(Long userId) {
        Set<String> jtis = redis.opsForSet().members(userKey(userId));
        return jtis == null ? Set.of() : new HashSet<>(jtis);
    }

    private String userKey(Long userId) {
        return USER_KEY_PREFIX + userId;
    }

    private String metaKey(String jti) {
        return META_KEY_PREFIX + jti;
    }
}
