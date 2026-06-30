package com.workouttracker.auth.email;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis 기반 {@link EmailVerificationStore} 구현.
 *
 * <p>{@code RedisRefreshTokenStore} 패턴을 따른다 — {@link StringRedisTemplate} 직접 사용,
 * 키 prefix 상수화, 도메인 독립.</p>
 *
 * <h3>키 구조 (ev: 네임스페이스)</h3>
 * <pre>
 *   ev:code:{email}            STRING  값 = 코드 SHA-256 해시,  TTL 600s
 *   ev:resend:{email}          STRING  값 = "1",  SET NX EX 60 (재발송 60초 쿨다운)
 *   ev:resend:hourly:{email}   STRING  값 = 카운트, INCR + EXPIRE 3600s (시간당 상한)
 *   ev:attempt:{email}         STRING  값 = 코드 실패 횟수, INCR (brute-force 방어)
 * </pre>
 *
 * <p>코드 평문은 절대 저장하지 않는다(해시만). 검증은 서비스가 입력 코드를 같은 방식으로
 * 해시해 일치 비교한다.</p>
 */
@Component
@RequiredArgsConstructor
public class RedisEmailVerificationStore implements EmailVerificationStore {

    private static final String CODE_KEY_PREFIX = "ev:code:";
    private static final String RESEND_KEY_PREFIX = "ev:resend:";
    private static final String RESEND_HOURLY_KEY_PREFIX = "ev:resend:hourly:";
    private static final String ATTEMPT_KEY_PREFIX = "ev:attempt:";

    /** 인증 코드 TTL — 10분. */
    private static final Duration CODE_TTL = Duration.ofSeconds(600);
    /** 재발송 쿨다운 — 60초. */
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    /** 시간당 재발송 카운터 윈도우 — 1시간. */
    private static final Duration HOURLY_WINDOW = Duration.ofSeconds(3600);
    /** 코드 실패 카운터는 코드 TTL 과 함께 자연 만료되도록 동일 TTL 적용. */
    private static final Duration ATTEMPT_TTL = CODE_TTL;

    private final StringRedisTemplate redis;

    @Override
    public void saveCode(String email, String codeHash) {
        redis.opsForValue().set(codeKey(email), codeHash, CODE_TTL);
        // 새 코드 발급 시 옛 실패 누적을 초기화 → 새 코드는 실패 0부터 시작(조기 429 방지).
        redis.delete(attemptKey(email));
    }

    @Override
    public Optional<String> findCodeHash(String email) {
        return Optional.ofNullable(redis.opsForValue().get(codeKey(email)));
    }

    @Override
    public void deleteCode(String email) {
        // 코드와 실패 카운터를 함께 제거 (검증 성공 또는 brute-force 차단 시 초기화)
        redis.delete(codeKey(email));
        redis.delete(attemptKey(email));
    }

    @Override
    public boolean tryAcquireResendCooldown(String email) {
        // SET ev:resend:{email} 1 NX EX 60 — 원자적. 새로 획득하면 true.
        Boolean acquired = redis.opsForValue()
                .setIfAbsent(resendKey(email), "1", RESEND_COOLDOWN);
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public boolean incrementHourlyResendAndCheck(String email, int maxPerHour) {
        String key = resendHourlyKey(email);
        Long count = redis.opsForValue().increment(key);
        // 첫 증가(1)일 때만 윈도우 TTL 설정 → 1시간 슬라이딩 카운터.
        if (count != null && count == 1L) {
            redis.expire(key, HOURLY_WINDOW);
        }
        return count != null && count <= maxPerHour;
    }

    @Override
    public long incrementAttempt(String email) {
        String key = attemptKey(email);
        Long count = redis.opsForValue().increment(key);
        // 카운터에 TTL 이 없으면 코드 만료 후에도 남으므로, 첫 증가 시 코드와 동일 TTL 부여.
        if (count != null && count == 1L) {
            redis.expire(key, ATTEMPT_TTL);
        }
        return count == null ? 0L : count;
    }

    private String codeKey(String email) {
        return CODE_KEY_PREFIX + email;
    }

    private String resendKey(String email) {
        return RESEND_KEY_PREFIX + email;
    }

    private String resendHourlyKey(String email) {
        return RESEND_HOURLY_KEY_PREFIX + email;
    }

    private String attemptKey(String email) {
        return ATTEMPT_KEY_PREFIX + email;
    }
}
