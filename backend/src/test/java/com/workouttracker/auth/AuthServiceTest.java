package com.workouttracker.auth;

import com.workouttracker.auth.dto.LoginRequest;
import com.workouttracker.auth.dto.LoginResponse;
import com.workouttracker.auth.dto.SignupRequest;
import com.workouttracker.auth.dto.SignupResponse;
import com.workouttracker.auth.jwt.JwtTokenProvider;
import com.workouttracker.common.error.BusinessException;
import com.workouttracker.common.error.ErrorCode;
import com.workouttracker.user.User;
import com.workouttracker.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 단위 테스트")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    private User existingUser;

    @BeforeEach
    void setUp() {
        existingUser = User.builder()
                .email("kim@example.com")
                .passwordHash("$2a$10$hashedPassword")
                .nickname("kim")
                .build();
        ReflectionTestUtils.setField(existingUser, "id", 1L);
    }

    @Test
    @DisplayName("회원가입 성공 - 이메일 중복 없으면 BCrypt 해싱 후 저장")
    void signup_success() {
        // given
        SignupRequest request = new SignupRequest("new@example.com", "Secret1234", "neo");
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret1234")).thenReturn("$2a$10$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 42L);
            return u;
        });

        // when
        SignupResponse response = authService.signup(request);

        // then
        assertThat(response.userId()).isEqualTo(42L);
        assertThat(response.email()).isEqualTo("new@example.com");
        assertThat(response.nickname()).isEqualTo("neo");
        verify(passwordEncoder, times(1)).encode("Secret1234");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 중복 시 EMAIL_DUPLICATED")
    void signup_emailDuplicated() {
        // given
        SignupRequest request = new SignupRequest("kim@example.com", "Secret1234", "kim");
        when(userRepository.existsByEmail("kim@example.com")).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_DUPLICATED);

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("로그인 성공 - 비밀번호 일치 시 JWT 발급")
    void login_success() {
        // given
        LoginRequest request = new LoginRequest("kim@example.com", "Secret1234");
        when(userRepository.findByEmail("kim@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("Secret1234", existingUser.getPasswordHash())).thenReturn(true);
        when(jwtTokenProvider.generateToken(1L, "kim@example.com")).thenReturn("jwt-token");
        when(jwtTokenProvider.getExpiresInSeconds()).thenReturn(3600L);

        // when
        LoginResponse response = authService.login(request);

        // then
        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600L);
    }

    @Test
    @DisplayName("로그인 실패 - 이메일 없음 / 비밀번호 불일치 모두 INVALID_CREDENTIALS")
    void login_invalidCredentials() {
        // case 1: 이메일 자체가 없음
        LoginRequest noEmailReq = new LoginRequest("nobody@example.com", "Secret1234");
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(noEmailReq))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        // case 2: 비밀번호 불일치
        LoginRequest wrongPwReq = new LoginRequest("kim@example.com", "WrongPass1");
        when(userRepository.findByEmail("kim@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches(eq("WrongPass1"), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(wrongPwReq))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verify(jwtTokenProvider, never()).generateToken(any(), anyString());
    }
}
