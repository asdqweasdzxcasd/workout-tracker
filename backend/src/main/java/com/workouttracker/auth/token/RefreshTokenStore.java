package com.workouttracker.auth.token;

import java.util.Set;

/**
 * Refresh Token 저장소 (도메인 독립적 추상화).
 *
 * <p>Redis 구현({@link RedisRefreshTokenStore})이 기본. 다른 백엔드(DynamoDB, JPA 등)로
 * 교체하려면 이 인터페이스만 새로 구현하면 된다.</p>
 *
 * <p>키 의미:</p>
 * <ul>
 *   <li><b>userId</b>: 사용자 식별자</li>
 *   <li><b>jti</b>: Refresh Token 의 JWT ID 클레임(회전 식별자)</li>
 * </ul>
 *
 * <p>다중 기기 지원 — 한 userId 에 여러 jti 가 동시 활성 가능.</p>
 *
 * <h3>회전(rotation) 시 사용 패턴</h3>
 * <pre>
 *   1. 클라이언트가 refresh 토큰 제출
 *   2. {@link #exists(Long, String)} 로 활성 여부 확인 (false 이면 재사용 의심)
 *   3. {@link #delete(Long, String)} 로 옛 jti 즉시 제거
 *   4. 새 jti 생성 → {@link #save(Long, String, long)}
 * </pre>
 */
public interface RefreshTokenStore {

    /** 새 refresh token (jti) 저장. ttlSeconds 후 자동 만료. */
    void save(Long userId, String jti, long ttlSeconds);

    /** 지정 jti 가 해당 userId 에 활성 상태인지. rotation 시 옛 jti 검증에 사용. */
    boolean exists(Long userId, String jti);

    /** 특정 jti 만 무효화 (개별 기기 로그아웃 또는 rotation 시 옛 토큰 제거). */
    void delete(Long userId, String jti);

    /** 해당 userId 의 모든 활성 jti 무효화 (전체 기기 로그아웃 또는 도난 탐지 시). */
    void deleteAllByUser(Long userId);

    /** 디버그/운영용 — 해당 userId 의 활성 jti 목록. */
    Set<String> findAllJtiByUser(Long userId);
}
