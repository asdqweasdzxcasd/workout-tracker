package com.workouttracker.auth.email;

import java.util.Optional;

/**
 * 이메일 인증 상태 저장소 (도메인 독립적 추상화).
 *
 * <p>Redis 구현({@link RedisEmailVerificationStore})이 기본.
 * {@code RefreshTokenStore} 와 동일하게 인프라(Redis)를 추상화한다.</p>
 *
 * <h3>키 의미 (ev: 네임스페이스)</h3>
 * <ul>
 *   <li><b>코드</b>: 이메일별 1개. SHA-256 해시로만 저장(평문 금지), TTL 600초.</li>
 *   <li><b>재발송 쿨다운</b>: 60초 단위 NX 잠금.</li>
 *   <li><b>시간당 재발송 상한</b>: 1시간 슬라이딩 카운터.</li>
 *   <li><b>코드 입력 실패</b>: brute-force 방어용 카운터.</li>
 * </ul>
 */
public interface EmailVerificationStore {

    /**
     * 인증 코드 해시 저장 (TTL 600초). 기존 코드가 있으면 덮어쓴다.
     *
     * <p>새 코드는 실패 0부터 시작해야 하므로 기존 실패 카운터({@code ev:attempt})를 함께
     * 초기화한다. 그러지 않으면 옛 코드의 실패 누적이 남아 새 코드인데 조기 429 가 발생한다.
     */
    void saveCode(String email, String codeHash);

    /** 저장된 코드 해시 조회. 없으면(미발급/만료) empty. */
    Optional<String> findCodeHash(String email);

    /** 코드 및 관련 실패 카운터 삭제 (검증 성공 또는 brute-force 차단 시). */
    void deleteCode(String email);

    /**
     * 재발송 쿨다운 잠금 시도 (60초). 원자적.
     *
     * @return 잠금을 새로 획득하면 true(=발송 허용), 이미 잠겨 있으면 false(=쿨다운 중)
     */
    boolean tryAcquireResendCooldown(String email);

    /**
     * 시간당 재발송 횟수 증가 후, 상한(maxPerHour) 이내인지 반환.
     *
     * @return 증가 후 카운트가 상한 이하이면 true(=허용), 초과면 false(=차단)
     */
    boolean incrementHourlyResendAndCheck(String email, int maxPerHour);

    /**
     * 코드 입력 실패 횟수 증가 후 반환.
     *
     * @return 증가 후 누적 실패 횟수
     */
    long incrementAttempt(String email);
}
