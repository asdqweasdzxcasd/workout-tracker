package com.workouttracker.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
