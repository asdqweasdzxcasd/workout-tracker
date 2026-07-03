package com.workouttracker.auth.email;

import com.workouttracker.common.error.BusinessException;
import com.workouttracker.common.error.ErrorCode;
import com.workouttracker.user.User;
import com.workouttracker.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link EmailVerificationService} 단위 테스트 (Mockito).
 *
 * <p>{@code AuthServiceTest} 컨벤션을 따른다 — store/sender/repo 를 mock 하고
 * <b>관찰 가능한 행동</b>(활성화 호출 / 코드 삭제 / 예외 코드 / 발송 여부)을 검증한다.
 * SHA-256 해시는 프로덕션과 동일한 방식으로 테스트에서 재현해 {@code findCodeHash} 스텁에 쓴다.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmailVerificationService 단위 테스트")
class EmailVerificationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailVerificationStore store;

    @Mock
    private EmailSender emailSender;

    private EmailVerificationService service;

    private static final String APP_NAME = "workout-tracker";
    private static final String EMAIL = "kim@example.com";
    private static final String CODE = "012345";        // 앞자리 0 보존 케이스
    private static final String CODE_HASH = sha256(CODE);

    @BeforeEach
    void setUp() {
        // @Value 로 주입되는 appName 은 생성자 직접 호출로 전달.
        // 테스트 전용 코드 기록기는 단위 테스트 범위 밖이므로 Optional.empty() (prod 와 동일하게 no-op).
        service = new EmailVerificationService(userRepository, store, emailSender, APP_NAME, Optional.empty());
    }

    /** 프로덕션과 동일한 SHA-256(16진 소문자) — findCodeHash 스텁용. */
    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private User unverifiedUser(String email, String nickname) {
        User u = User.builder().email(email).passwordHash("$2a$10$x").nickname(nickname).build();
        ReflectionTestUtils.setField(u, "id", 1L);
        return u;
    }

    private User verifiedUser(String email) {
        User u = unverifiedUser(email, "kim");
        u.markEmailVerified();
        return u;
    }

    // ====================================================================
    // verify
    // ====================================================================

    @Nested
    @DisplayName("verify - 코드 검증")
    class Verify {

        @Test
        @DisplayName("성공 - 올바른 코드면 활성화 호출 + 코드 삭제")
        void verify_success() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverifiedUser(EMAIL, "kim")));
            when(store.findCodeHash(EMAIL)).thenReturn(Optional.of(CODE_HASH));
            when(userRepository.activateEmailVerification(eq(EMAIL), any(OffsetDateTime.class))).thenReturn(1);

            service.verify(EMAIL, CODE);

            verify(store, times(1)).deleteCode(EMAIL);
            verify(userRepository, times(1)).activateEmailVerification(eq(EMAIL), any(OffsetDateTime.class));
            verify(store, never()).incrementAttempt(anyString());
        }

        @Test
        @DisplayName("실패 - 틀린 코드면 INVALID_VERIFICATION_CODE + attempt 증가, 활성화 안 함")
        void verify_wrongCode() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverifiedUser(EMAIL, "kim")));
            when(store.findCodeHash(EMAIL)).thenReturn(Optional.of(CODE_HASH));
            when(store.incrementAttempt(EMAIL)).thenReturn(1L);

            assertThatThrownBy(() -> service.verify(EMAIL, "999999"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE);

            verify(store, times(1)).incrementAttempt(EMAIL);
            verify(userRepository, never()).activateEmailVerification(anyString(), any(OffsetDateTime.class));
            verify(store, never()).deleteCode(anyString());
        }

        @Test
        @DisplayName("만료/없음 - 코드 해시 없으면 VERIFICATION_CODE_EXPIRED")
        void verify_expired() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverifiedUser(EMAIL, "kim")));
            when(store.findCodeHash(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.verify(EMAIL, CODE))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VERIFICATION_CODE_EXPIRED);

            verify(store, never()).incrementAttempt(anyString());
            verify(userRepository, never()).activateEmailVerification(anyString(), any(OffsetDateTime.class));
        }

        @Test
        @DisplayName("시도 초과 - attempt 가 5 초과면 TOO_MANY_ATTEMPTS + 코드 폐기")
        void verify_tooManyAttempts() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverifiedUser(EMAIL, "kim")));
            when(store.findCodeHash(EMAIL)).thenReturn(Optional.of(CODE_HASH));
            when(store.incrementAttempt(EMAIL)).thenReturn(6L); // MAX_ATTEMPTS(5) 초과

            assertThatThrownBy(() -> service.verify(EMAIL, "999999"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOO_MANY_ATTEMPTS);

            verify(store, times(1)).deleteCode(EMAIL); // 코드 무효화
            verify(userRepository, never()).activateEmailVerification(anyString(), any(OffsetDateTime.class));
        }

        @Test
        @DisplayName("경계 - attempt 가 정확히 5(상한)면 아직 INVALID, 폐기 안 함")
        void verify_attemptExactlyAtLimit() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverifiedUser(EMAIL, "kim")));
            when(store.findCodeHash(EMAIL)).thenReturn(Optional.of(CODE_HASH));
            when(store.incrementAttempt(EMAIL)).thenReturn(5L);

            assertThatThrownBy(() -> service.verify(EMAIL, "999999"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE);

            verify(store, never()).deleteCode(anyString());
        }

        @Test
        @DisplayName("멱등 - 이미 인증된 사용자면 코드 조회/소모 없이 예외 없이 통과")
        void verify_alreadyVerified_idempotent() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(verifiedUser(EMAIL)));

            service.verify(EMAIL, CODE); // 예외 없어야 함

            verify(store, never()).findCodeHash(anyString());
            verify(store, never()).deleteCode(anyString());
            verify(userRepository, never()).activateEmailVerification(anyString(), any(OffsetDateTime.class));
        }
    }

    // ====================================================================
    // 대소문자 정규화 (Blocker 회귀 방지)
    // ====================================================================

    @Nested
    @DisplayName("대소문자 정규화 - Redis 키/카운터는 대소문자 무관")
    class CaseNormalization {

        @Test
        @DisplayName("Kim@Example.com 으로 발급 → kim@example.com 으로 verify 성공 (같은 정규화 키)")
        void issuedWithMixedCase_verifiedWithLowerCase() {
            // 1) 대문자 이메일로 발급 — store.saveCode 의 키(소문자)와 본문의 실제 코드를 캡처
            ArgumentCaptor<String> savedEmail = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> savedHash = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> sentBody = ArgumentCaptor.forClass(String.class);

            service.issueAndSend("Kim@Example.com", "kim");

            verify(store).saveCode(savedEmail.capture(), savedHash.capture());
            verify(emailSender).send(eq("kim@example.com"), anyString(), sentBody.capture());
            assertThat(savedEmail.getValue())
                    .as("저장 키는 소문자 정규화되어야 함")
                    .isEqualTo("kim@example.com");

            // 본문에서 실제 발급 코드를 추출 (SecureRandom 이라 미리 알 수 없음)
            String issuedCode = extractCodeFromBody(sentBody.getValue());

            // 2) 소문자 이메일로 verify — 같은 정규화 키로 조회되어 성공해야 함.
            //    findCodeHash 는 발급 때 저장된 해시(savedHash)를 그대로 돌려주도록 스텁.
            when(userRepository.findByEmail("kim@example.com"))
                    .thenReturn(Optional.of(unverifiedUser("kim@example.com", "kim")));
            when(store.findCodeHash("kim@example.com")).thenReturn(Optional.of(savedHash.getValue()));
            when(userRepository.activateEmailVerification(eq("kim@example.com"), any(OffsetDateTime.class))).thenReturn(1);

            service.verify("kim@example.com", issuedCode);

            verify(userRepository, times(1)).activateEmailVerification(eq("kim@example.com"), any(OffsetDateTime.class));
            verify(store, times(1)).deleteCode("kim@example.com");
        }

        /** 인증 메일 본문에서 "인증 코드: 012345" 패턴의 6자리 코드를 추출. */
        private String extractCodeFromBody(String body) {
            var m = java.util.regex.Pattern.compile("인증 코드: (\\d{6})").matcher(body);
            assertThat(m.find()).as("본문에 6자리 코드가 있어야 함: %s", body).isTrue();
            return m.group(1);
        }

        @Test
        @DisplayName("attempt 키도 대소문자 무관 - Kim@Example.com 으로 verify 실패 시 정규화 키로 attempt 증가")
        void attemptKeyIsCaseInsensitive() {
            when(userRepository.findByEmail("kim@example.com"))
                    .thenReturn(Optional.of(unverifiedUser("kim@example.com", "kim")));
            when(store.findCodeHash("kim@example.com")).thenReturn(Optional.of(CODE_HASH));
            when(store.incrementAttempt("kim@example.com")).thenReturn(1L);

            assertThatThrownBy(() -> service.verify("Kim@Example.com", "999999"))
                    .isInstanceOf(BusinessException.class);

            // 대문자 입력이었지만 정규화된 소문자 키로 attempt 가 증가해야 함
            verify(store, times(1)).incrementAttempt("kim@example.com");
            verify(store, never()).incrementAttempt("Kim@Example.com");
        }

        @Test
        @DisplayName("verify 도 대소문자/공백 무관 - 정규화 후 findCodeHash/activate 호출")
        void verifyNormalizesEmail() {
            when(userRepository.findByEmail("kim@example.com"))
                    .thenReturn(Optional.of(unverifiedUser("kim@example.com", "kim")));
            when(store.findCodeHash("kim@example.com")).thenReturn(Optional.of(CODE_HASH));
            when(userRepository.activateEmailVerification(eq("kim@example.com"), any(OffsetDateTime.class))).thenReturn(1);

            service.verify("  Kim@Example.COM  ", CODE);

            verify(store, times(1)).findCodeHash("kim@example.com");
            verify(userRepository, times(1)).activateEmailVerification(eq("kim@example.com"), any(OffsetDateTime.class));
        }
    }

    // ====================================================================
    // resend - 레이트리밋 / 열거 방어
    // ====================================================================

    @Nested
    @DisplayName("resend - 재발송 레이트리밋 & 열거 방어")
    class Resend {

        @Test
        @DisplayName("쿨다운 - 60초 내 2회차(쿨다운 잠금 실패)면 RESEND_RATE_LIMITED")
        void resend_cooldown() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverifiedUser(EMAIL, "kim")));
            when(store.tryAcquireResendCooldown(EMAIL)).thenReturn(false); // 잠금 이미 점유됨

            assertThatThrownBy(() -> service.resend(EMAIL))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESEND_RATE_LIMITED);

            verify(emailSender, never()).send(anyString(), anyString(), anyString());
            verify(store, never()).incrementHourlyResendAndCheck(anyString(), anyInt());
        }

        @Test
        @DisplayName("시간당 상한 - 쿨다운 통과하나 시간당 상한 초과면 RESEND_RATE_LIMITED")
        void resend_hourlyLimit() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverifiedUser(EMAIL, "kim")));
            when(store.tryAcquireResendCooldown(EMAIL)).thenReturn(true);
            when(store.incrementHourlyResendAndCheck(eq(EMAIL), anyInt())).thenReturn(false);

            assertThatThrownBy(() -> service.resend(EMAIL))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESEND_RATE_LIMITED);

            verify(emailSender, never()).send(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("성공 - 미인증 가입 사용자면 쿨다운/상한 통과 후 코드 발급 & 발송")
        void resend_success() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverifiedUser(EMAIL, "kim")));
            when(store.tryAcquireResendCooldown(EMAIL)).thenReturn(true);
            when(store.incrementHourlyResendAndCheck(eq(EMAIL), anyInt())).thenReturn(true);

            service.resend(EMAIL);

            verify(store, times(1)).saveCode(eq(EMAIL), anyString());
            verify(emailSender, times(1)).send(eq(EMAIL), anyString(), anyString());
        }

        @Test
        @DisplayName("열거 방어 - 미가입 이메일은 예외 없이 종료, 발송/레이트리밋 카운터 모두 미호출")
        void resend_unknownEmail_silentNoSend() {
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            service.resend("ghost@example.com"); // 예외 없음 (202 경로)

            verify(emailSender, never()).send(anyString(), anyString(), anyString());
            verify(store, never()).tryAcquireResendCooldown(anyString());
            verify(store, never()).incrementHourlyResendAndCheck(anyString(), anyInt());
            verify(store, never()).saveCode(anyString(), anyString());
        }

        @Test
        @DisplayName("열거 방어 - 이미 인증된 이메일도 예외 없이 종료, 발송/카운터 미호출")
        void resend_alreadyVerified_silentNoSend() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(verifiedUser(EMAIL)));

            service.resend(EMAIL); // 예외 없음

            verify(emailSender, never()).send(anyString(), anyString(), anyString());
            verify(store, never()).tryAcquireResendCooldown(anyString());
            verify(store, never()).saveCode(anyString(), anyString());
        }

        @Test
        @DisplayName("열거 방어 + 정규화 - 대문자 미가입 이메일도 정규화 후 조회, 조용히 종료")
        void resend_normalizesAndSilent() {
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            service.resend("Ghost@Example.com");

            verify(userRepository, times(1)).findByEmail("ghost@example.com");
            verifyNoInteractions(emailSender);
        }
    }

    // ====================================================================
    // 코드 형식 - issueAndSend 가 발급하는 코드
    // ====================================================================

    @Nested
    @DisplayName("issueAndSend - 코드 생성/발송")
    class IssueAndSend {

        @Test
        @DisplayName("발급 코드는 ^\\\\d{6}$ (앞자리 0 보존) - 본문에서 캡처해 검증")
        void generatedCodeIsSixDigits() {
            ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);

            // 여러 번 발급해 앞자리 0 / 형식을 통계적으로 확인 (SecureRandom 이라 값은 매번 다름)
            for (int i = 0; i < 200; i++) {
                service.issueAndSend(EMAIL, "kim");
            }

            verify(emailSender, times(200)).send(eq(EMAIL), anyString(), body.capture());
            for (String b : body.getAllValues()) {
                String code = extractCode(b);
                assertThat(code)
                        .as("발급 코드는 정확히 6자리 숫자여야 함 (앞자리 0 포함). body=%s", b)
                        .matches("^\\d{6}$");
            }
        }

        @Test
        @DisplayName("저장 해시 ≠ 평문 - saveCode 에 SHA-256(16진 64자) 해시가 전달됨")
        void storedValueIsHashedNotPlaintext() {
            ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);

            service.issueAndSend(EMAIL, "kim");

            verify(store).saveCode(eq(EMAIL), hash.capture());
            assertThat(hash.getValue())
                    .as("코드 평문이 아닌 SHA-256 16진 해시여야 함")
                    .matches("^[0-9a-f]{64}$");
        }

        @Test
        @DisplayName("발송 실패해도 예외 전파 안 함 - SES 리젝 등 흡수, 코드는 저장됨 (회귀: resend 500 방지)")
        void sendFailureIsSwallowed() {
            doThrow(new RuntimeException("MessageRejected: not verified"))
                    .when(emailSender).send(eq(EMAIL), anyString(), anyString());

            assertThatCode(() -> service.issueAndSend(EMAIL, "kim")).doesNotThrowAnyException();

            verify(store).saveCode(eq(EMAIL), anyString());
        }

        /** 본문에서 "인증 코드: 012345" 패턴의 6자리 코드를 추출. */
        private String extractCode(String body) {
            var m = java.util.regex.Pattern.compile("인증 코드: (\\d{6})").matcher(body);
            assertThat(m.find()).as("본문에 6자리 코드가 있어야 함: %s", body).isTrue();
            return m.group(1);
        }
    }
}
