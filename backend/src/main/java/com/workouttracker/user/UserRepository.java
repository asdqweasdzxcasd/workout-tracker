package com.workouttracker.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * User JPA 리포지토리.
 *
 * <p>{@code idx_users_email} 인덱스가 {@code LOWER(email)} 함수 인덱스이므로
 * 대소문자 무관 조회를 LOWER 비교로 수행한다.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /** 대소문자 무관 이메일 조회. */
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmail(@Param("email") String email);

    /** 회원가입 직전 중복 체크용. */
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    boolean existsByEmail(@Param("email") String email);

    /**
     * 이메일 인증 활성화 (멱등·원자적 조건부 UPDATE).
     *
     * <p>{@code email_verified=false} 인 경우에만 true 로 갱신한다. 이미 인증된 경우 0행 반환
     * → 멱등 처리(서비스가 200 응답). 동시 호출에도 단일 UPDATE 라 안전하다.
     *
     * @return 갱신된 행 수 (0=이미 인증됨/대상 없음, 1=새로 인증 처리됨)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.emailVerified = true, u.updatedAt = :now "
            + "WHERE LOWER(u.email) = LOWER(:email) AND u.emailVerified = false")
    int activateEmailVerification(@Param("email") String email, @Param("now") OffsetDateTime now);
}
