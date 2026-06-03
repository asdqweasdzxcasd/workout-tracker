package com.workouttracker.auth;

import com.workouttracker.auth.dto.LoginRequest;
import com.workouttracker.auth.dto.LoginResponse;
import com.workouttracker.auth.dto.SignupRequest;
import com.workouttracker.auth.dto.SignupResponse;
import com.workouttracker.common.error.BusinessException;
import com.workouttracker.common.error.ErrorCode;
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

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Refresh Token + Redis 통합 테스트.
 *
 * <p>Testcontainers 가 redis:7-alpine 컨테이너를 띄우고 {@code @DynamicPropertySource} 로
 * 동적 host/port 를 주입한다. 검증 대상은 <b>관찰 가능한 행동</b>(엔드포인트 응답 + Redis 의
 * 활성 jti 멤버 수) — 키 prefix 같은 내부 구현 결합은 최소화.</p>
 *
 * <p>전제: Docker 가 실행 중이어야 한다. (사용자 PC: Docker Desktop)</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("Refresh Token + Redis 통합 테스트")
class AuthRedisIntegrationTest {

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
    AuthService authService;

    @Autowired
    StringRedisTemplate redisTemplate;

    private static final String PASSWORD = "Secret1234";

    private String email;       // 매 테스트마다 고유 (H2 가 컨텍스트 재사용 시 충돌 방지)
    private Long userId;

    @BeforeEach
    void setup() {
        // Redis 의 모든 키 정리 (이전 테스트 잔재 제거)
        Set<String> keys = redisTemplate.keys("*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        email = "u" + System.nanoTime() + "@test.com";
        SignupResponse signup = authService.signup(new SignupRequest(email, PASSWORD, "kim"));
        userId = signup.userId();
    }

    @Test
    @DisplayName("로그인 → Redis 에 활성 세션 1개 등록 + Refresh 토큰 반환")
    void login_persistsOneSession() {
        LoginResponse res = authService.login(new LoginRequest(email, PASSWORD));

        assertThat(res.accessToken()).isNotBlank();
        assertThat(res.refreshToken()).isNotBlank();
        assertThat(activeJtiCount(userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("refresh → 옛 세션 사라지고 새 세션 1개로 교체 (rotation)")
    void refresh_rotatesSession() {
        LoginResponse first = authService.login(new LoginRequest(email, PASSWORD));

        LoginResponse rotated = authService.refresh(first.refreshToken());

        assertThat(activeJtiCount(userId)).isEqualTo(1); // 여전히 한 기기
        assertThat(rotated.refreshToken()).isNotEqualTo(first.refreshToken()); // 새 refresh
        // 옛 refresh 로 다시 호출 시 REUSED (다음 테스트에서 별도 검증)
    }

    @Test
    @DisplayName("옛 refresh 재사용 → REUSED + 모든 세션 강제 무효화")
    void reuseOldRefresh_triggersDefenceAndClearsAllSessions() {
        LoginResponse first = authService.login(new LoginRequest(email, PASSWORD));
        authService.refresh(first.refreshToken()); // rotation 1

        assertThatThrownBy(() -> authService.refresh(first.refreshToken()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_REUSED);

        assertThat(activeJtiCount(userId)).isZero(); // 전체 무효화
    }

    @Test
    @DisplayName("다중 기기 — 두 번 로그인하면 세션 2개 공존")
    void multiDevice_twoLogins_keepsTwoSessions() {
        authService.login(new LoginRequest(email, PASSWORD));
        authService.login(new LoginRequest(email, PASSWORD));

        assertThat(activeJtiCount(userId)).isEqualTo(2);
    }

    @Test
    @DisplayName("logout(userId, refreshToken) — 해당 기기만 끄고 다른 기기 세션은 살아있음")
    void logout_singleSession_keepsOthers() {
        LoginResponse deviceA = authService.login(new LoginRequest(email, PASSWORD));
        LoginResponse deviceB = authService.login(new LoginRequest(email, PASSWORD));
        assertThat(activeJtiCount(userId)).isEqualTo(2);

        authService.logout(userId, deviceA.refreshToken());

        assertThat(activeJtiCount(userId)).isEqualTo(1);
        // deviceB 의 refresh 는 여전히 유효 → rotation 가능
        LoginResponse rotatedB = authService.refresh(deviceB.refreshToken());
        assertThat(rotatedB.refreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("logoutAll — 모든 활성 세션 무효화")
    void logoutAll_clearsEverything() {
        authService.login(new LoginRequest(email, PASSWORD));
        authService.login(new LoginRequest(email, PASSWORD));
        assertThat(activeJtiCount(userId)).isEqualTo(2);

        authService.logoutAll(userId);

        assertThat(activeJtiCount(userId)).isZero();
    }

    // ====================================================================
    // helpers
    // ====================================================================

    /** 해당 userId 의 활성 Refresh 세션(jti) 수. 키 prefix 는 RedisRefreshTokenStore 내부 규약. */
    private long activeJtiCount(Long userId) {
        Long size = redisTemplate.opsForSet().size("rt:" + userId);
        return size == null ? 0L : size;
    }
}
