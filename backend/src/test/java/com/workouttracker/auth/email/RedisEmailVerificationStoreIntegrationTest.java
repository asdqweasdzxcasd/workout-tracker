package com.workouttracker.auth.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RedisEmailVerificationStore} + Redis 통합 테스트.
 *
 * <p>{@code AuthRedisIntegrationTest} 컨벤션을 따른다 — Testcontainers redis:7-alpine,
 * {@code @DynamicPropertySource} 로 host/port 주입. 검증 대상은 <b>관찰 가능한 행동</b>
 * (라운드트립 / TTL 존재 / NX 동작 / INCR 만료 / attempt 리셋 / 키 정규화).</p>
 *
 * <p>전제: Docker 실행 필요. (사용자 PC 로컬 Docker 미가동 시 CI 에서 검증)</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("RedisEmailVerificationStore + Redis 통합 테스트")
class RedisEmailVerificationStoreIntegrationTest {

    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry r) {
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    EmailVerificationStore store;

    @Autowired
    StringRedisTemplate redisTemplate;

    private static final String EMAIL = "kim@example.com";
    private static final String CODE_HASH = "a".repeat(64); // 형식만 맞는 더미 해시

    @BeforeEach
    void clean() {
        Set<String> keys = redisTemplate.keys("ev:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // ====================================================================
    // 코드 라운드트립 + TTL
    // ====================================================================

    @Test
    @DisplayName("saveCode → findCodeHash → deleteCode 라운드트립")
    void codeRoundTrip() {
        assertThat(store.findCodeHash(EMAIL)).isEmpty();

        store.saveCode(EMAIL, CODE_HASH);
        assertThat(store.findCodeHash(EMAIL)).contains(CODE_HASH);

        store.deleteCode(EMAIL);
        assertThat(store.findCodeHash(EMAIL)).isEmpty();
    }

    @Test
    @DisplayName("saveCode 후 코드 키에 TTL(<=600s, >0) 이 적용된다")
    void saveCode_appliesTtl() {
        store.saveCode(EMAIL, CODE_HASH);

        Long ttl = redisTemplate.getExpire("ev:code:" + EMAIL, TimeUnit.SECONDS);
        assertThat(ttl).isNotNull();
        assertThat(ttl).isGreaterThan(0L).isLessThanOrEqualTo(600L);
    }

    // ====================================================================
    // 재발송 쿨다운 (NX)
    // ====================================================================

    @Test
    @DisplayName("tryAcquireResendCooldown - 1회차 true, 2회차 false (NX 잠금)")
    void cooldown_nxBehaviour() {
        assertThat(store.tryAcquireResendCooldown(EMAIL)).isTrue();
        assertThat(store.tryAcquireResendCooldown(EMAIL)).isFalse();

        // 쿨다운 키에 TTL(<=60s) 존재
        Long ttl = redisTemplate.getExpire("ev:resend:" + EMAIL, TimeUnit.SECONDS);
        assertThat(ttl).isNotNull();
        assertThat(ttl).isGreaterThan(0L).isLessThanOrEqualTo(60L);
    }

    // ====================================================================
    // 시간당 상한 (INCR + EXPIRE)
    // ====================================================================

    @Test
    @DisplayName("incrementHourlyResendAndCheck - 상한(3)까지 true, 초과 시 false")
    void hourlyLimit_incrAndExpire() {
        int max = 3;
        assertThat(store.incrementHourlyResendAndCheck(EMAIL, max)).isTrue();  // 1
        assertThat(store.incrementHourlyResendAndCheck(EMAIL, max)).isTrue();  // 2
        assertThat(store.incrementHourlyResendAndCheck(EMAIL, max)).isTrue();  // 3
        assertThat(store.incrementHourlyResendAndCheck(EMAIL, max)).isFalse(); // 4 > 3

        // 첫 증가에 윈도우 TTL(<=3600s) 부여됨
        Long ttl = redisTemplate.getExpire("ev:resend:hourly:" + EMAIL, TimeUnit.SECONDS);
        assertThat(ttl).isNotNull();
        assertThat(ttl).isGreaterThan(0L).isLessThanOrEqualTo(3600L);
    }

    // ====================================================================
    // attempt 증가 + 새 코드 발급 시 리셋 (#6)
    // ====================================================================

    @Test
    @DisplayName("incrementAttempt - 순차 증가 + 첫 증가에 TTL 부여")
    void incrementAttempt_countsAndTtl() {
        assertThat(store.incrementAttempt(EMAIL)).isEqualTo(1L);
        assertThat(store.incrementAttempt(EMAIL)).isEqualTo(2L);

        Long ttl = redisTemplate.getExpire("ev:attempt:" + EMAIL, TimeUnit.SECONDS);
        assertThat(ttl).isNotNull();
        assertThat(ttl).isGreaterThan(0L).isLessThanOrEqualTo(600L);
    }

    @Test
    @DisplayName("새 코드 발급(saveCode) 시 attempt 카운터가 0으로 리셋된다 (#6)")
    void saveCode_resetsAttempt() {
        store.incrementAttempt(EMAIL);
        store.incrementAttempt(EMAIL);
        assertThat(redisTemplate.opsForValue().get("ev:attempt:" + EMAIL)).isEqualTo("2");

        // 새 코드 발급 → attempt 키 삭제 (다음 증가는 다시 1부터)
        store.saveCode(EMAIL, CODE_HASH);

        assertThat(redisTemplate.opsForValue().get("ev:attempt:" + EMAIL)).isNull();
        assertThat(store.incrementAttempt(EMAIL)).isEqualTo(1L);
    }

    @Test
    @DisplayName("deleteCode 는 코드와 attempt 카운터를 함께 제거한다")
    void deleteCode_alsoClearsAttempt() {
        store.saveCode(EMAIL, CODE_HASH);
        store.incrementAttempt(EMAIL);

        store.deleteCode(EMAIL);

        assertThat(redisTemplate.opsForValue().get("ev:code:" + EMAIL)).isNull();
        assertThat(redisTemplate.opsForValue().get("ev:attempt:" + EMAIL)).isNull();
    }

    // ====================================================================
    // 키 정규화 — 호출자가 이미 소문자 정규화한 키를 넘긴다는 계약 확인
    // (store 자체는 키를 변형하지 않으므로, 같은 문자열이면 같은 키를 공유)
    // ====================================================================

    @Test
    @DisplayName("동일 정규화 키는 같은 Redis 엔트리를 공유한다")
    void sameNormalizedKeySharesEntry() {
        // 서비스가 normalize() 로 소문자화한 뒤 store 를 호출하므로,
        // store 레벨에서는 동일 문자열 키가 같은 코드/카운터를 가리켜야 한다.
        String normalized = "kim@example.com";

        store.saveCode(normalized, CODE_HASH);
        Optional<String> found = store.findCodeHash(normalized);

        assertThat(found).contains(CODE_HASH);
        // Redis 키는 정확히 정규화된 형태로만 존재
        assertThat(redisTemplate.hasKey("ev:code:kim@example.com")).isTrue();
        assertThat(redisTemplate.hasKey("ev:code:Kim@Example.com")).isFalse();
    }
}
