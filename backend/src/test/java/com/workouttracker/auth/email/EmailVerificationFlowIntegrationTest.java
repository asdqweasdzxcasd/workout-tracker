package com.workouttracker.auth.email;

import com.workouttracker.auth.AuthService;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 이메일 인증 전체 흐름 통합 테스트 (선택 — C).
 *
 * <p>signup → (발급 코드 확보) → verify → login 성공, 그리고 인증 전 login 은
 * {@code EMAIL_NOT_VERIFIED}(403) 임을 검증한다. 발급 코드는 테스트용 녹화
 * {@link EmailSender}(@Primary) 로 본문에서 추출한다.</p>
 *
 * <p>전제: Docker 실행 필요. 미가동 시 CI 에서 검증.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("이메일 인증 전체 흐름 통합 테스트")
class EmailVerificationFlowIntegrationTest {

    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry r) {
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    /** 발송 본문을 메모리에 기록해 인증 코드를 테스트에서 추출하기 위한 녹화 EmailSender. */
    @TestConfiguration
    static class RecordingEmailSenderConfig {
        @Bean
        @Primary
        RecordingEmailSender recordingEmailSender() {
            return new RecordingEmailSender();
        }
    }

    static class RecordingEmailSender implements EmailSender {
        final ConcurrentHashMap<String, String> lastBodyByEmail = new ConcurrentHashMap<>();

        @Override
        public void send(String to, String subject, String body) {
            lastBodyByEmail.put(to, body);
        }

        String codeFor(String email) {
            String body = lastBodyByEmail.get(email.trim().toLowerCase());
            assertThat(body).as("발송 본문이 기록되어야 함: %s", email).isNotNull();
            Matcher m = Pattern.compile("인증 코드: (\\d{6})").matcher(body);
            assertThat(m.find()).isTrue();
            return m.group(1);
        }
    }

    @Autowired
    AuthService authService;

    @Autowired
    EmailVerificationService emailVerificationService;

    @Autowired
    RecordingEmailSender recordingEmailSender;

    @Autowired
    StringRedisTemplate redisTemplate;

    private static final String PASSWORD = "Secret1234";
    private String email;

    @BeforeEach
    void setup() {
        Set<String> keys = redisTemplate.keys("*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        email = "flow" + System.nanoTime() + "@test.com";
    }

    @Test
    @DisplayName("인증 전 login 은 EMAIL_NOT_VERIFIED(403), 인증 후 login 성공")
    void signupVerifyLoginFlow() {
        SignupResponse signup = authService.signup(new SignupRequest(email, PASSWORD, "kim"));
        assertThat(signup.emailVerified()).isFalse();

        // 인증 전 로그인 → 403 EMAIL_NOT_VERIFIED
        assertThatThrownBy(() -> authService.login(new LoginRequest(email, PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);

        // 코드 확보: @Async 리스너 타이밍에 의존하지 않도록 issueAndSend 를 직접 호출해 결정적으로 발급
        emailVerificationService.issueAndSend(email, "kim");
        String code = recordingEmailSender.codeFor(email);
        assertThat(code).matches("^\\d{6}$");

        // verify
        emailVerificationService.verify(email, code);

        // 인증 후 로그인 성공
        LoginResponse res = authService.login(new LoginRequest(email, PASSWORD));
        assertThat(res.accessToken()).isNotBlank();
        assertThat(res.refreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("대소문자 회귀 - 대문자 이메일로 발급한 코드를 소문자로 verify 후 login 성공")
    void caseInsensitiveEndToEnd() {
        String upper = email.toUpperCase();
        authService.signup(new SignupRequest(email, PASSWORD, "kim"));

        // 대문자 이메일로 발급
        emailVerificationService.issueAndSend(upper, "kim");
        String code = recordingEmailSender.codeFor(email); // 정규화 키(소문자)로 기록됨

        // 소문자 이메일로 verify
        emailVerificationService.verify(email, code);

        LoginResponse res = authService.login(new LoginRequest(email, PASSWORD));
        assertThat(res.accessToken()).isNotBlank();
    }
}
