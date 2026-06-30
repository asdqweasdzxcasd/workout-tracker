package com.workouttracker.auth.email;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * E2E 테스트 전용 — 마지막으로 발급된 평문 인증 코드를 임시 기록한다.
 *
 * <p><b>운영(prod)에는 이 빈 자체가 존재하지 않는다</b>({@code @Profile("!prod")}).
 * {@link EmailVerificationService} 는 이 컴포넌트를 {@code Optional} 로 주입받아,
 * 빈이 없는 prod 에서는 기록 자체가 no-op 이 되도록 한다. 따라서 평문 코드는 prod 어디에도
 * 저장/노출되지 않는다.</p>
 *
 * <p>E2E(Playwright)는 SES/메일함을 거치지 않고 발급 코드를 알아야 한다. {@code !prod} 환경에서만
 * 평문 코드를 짧은 TTL 로 Redis 에 적재하고, {@code TestSupportController} 가 이를 조회해 돌려준다.
 * 실제 인증 코드 검증 키({@code ev:code:})는 여전히 해시만 저장한다 — 본 기록은 완전히 분리된
 * 별도 키({@code test:lastcode:})를 사용한다.</p>
 */
@Component
@Profile("!prod")
@RequiredArgsConstructor
public class TestVerificationCodeRecorder {

    /** E2E 전용 평문 코드 키 prefix. 운영 키 네임스페이스(ev:)와 분리. */
    private static final String LAST_CODE_KEY_PREFIX = "test:lastcode:";
    /** 짧은 TTL — E2E 가 즉시 소비하므로 5분이면 충분. */
    private static final Duration LAST_CODE_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redis;

    /** 마지막 발급 평문 코드를 정규화된 이메일 키로 임시 저장 (덮어쓰기). */
    public void record(String normalizedEmail, String plainCode) {
        redis.opsForValue().set(lastCodeKey(normalizedEmail), plainCode, LAST_CODE_TTL);
    }

    /** 저장된 평문 코드 조회. 없으면(미발급/만료) empty. */
    public Optional<String> findLastCode(String normalizedEmail) {
        return Optional.ofNullable(redis.opsForValue().get(lastCodeKey(normalizedEmail)));
    }

    private String lastCodeKey(String normalizedEmail) {
        return LAST_CODE_KEY_PREFIX + normalizedEmail;
    }
}
