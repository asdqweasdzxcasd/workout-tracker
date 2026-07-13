package com.workouttracker.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UserRepository} 의 OAuth(D.3) 관련 매핑/조회 검증.
 *
 * <p>H2(PostgreSQL 호환) create-drop 환경. 검증 대상:
 * 소셜 사용자 저장/조회, 로컬 사용자 다수 공존(provider NULL 유니크 비충돌), 계정 연동.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("UserRepository OAuth 매핑/조회")
class UserRepositoryOAuthTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("소셜 사용자 저장 후 provider+providerId 로 조회된다 (비밀번호 없음, 이메일 인증됨)")
    void saveAndFindSocialUser() {
        User social = User.ofSocial(AuthProvider.GOOGLE, "google-sub-123", "someone@gmail.com", "someone");
        userRepository.save(social);

        Optional<User> found = userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-123");

        assertThat(found).isPresent();
        assertThat(found.get().getPasswordHash()).isNull();
        assertThat(found.get().isEmailVerified()).isTrue();
        assertThat(found.get().getProvider()).isEqualTo(AuthProvider.GOOGLE);
    }

    @Test
    @DisplayName("provider 가 다르면 같은 providerId 라도 서로 다른 사용자로 조회된다")
    void differentProviderSameId() {
        userRepository.save(User.ofSocial(AuthProvider.NAVER, "id-1", "a@naver.com", "a"));
        userRepository.save(User.ofSocial(AuthProvider.KAKAO, "id-1", "b@kakao.com", "b"));

        assertThat(userRepository.findByProviderAndProviderId(AuthProvider.NAVER, "id-1")).isPresent();
        assertThat(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "id-1")).isPresent();
    }

    @Test
    @DisplayName("로컬 사용자(provider NULL) 는 여러 명이어도 유니크 제약에 걸리지 않는다")
    void multipleLocalUsersCoexist() {
        userRepository.save(User.builder().email("l1@ex.com").passwordHash("h1").nickname("l1").build());
        userRepository.save(User.builder().email("l2@ex.com").passwordHash("h2").nickname("l2").build());

        assertThat(userRepository.count()).isEqualTo(2);
        assertThat(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "x")).isEmpty();
    }

    @Test
    @DisplayName("linkSocial 로 기존 로컬 계정에 제공자를 연동할 수 있다")
    void linkSocialToLocalUser() {
        User local = userRepository.save(
                User.builder().email("link@ex.com").passwordHash("h").nickname("link").build());

        local.linkSocial(AuthProvider.GOOGLE, "google-sub-999");
        userRepository.saveAndFlush(local);

        Optional<User> found = userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-999");
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("link@ex.com");
        assertThat(found.get().getPasswordHash()).isEqualTo("h"); // 기존 비밀번호 유지
    }
}
