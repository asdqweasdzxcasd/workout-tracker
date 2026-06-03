package com.workouttracker.auth;

import com.workouttracker.auth.dto.LoginRequest;
import com.workouttracker.auth.dto.LoginResponse;
import com.workouttracker.auth.dto.SignupRequest;
import com.workouttracker.auth.dto.SignupResponse;
import com.workouttracker.auth.jwt.JwtTokenProvider;
import com.workouttracker.auth.token.RefreshTokenStore;
import com.workouttracker.common.error.BusinessException;
import com.workouttracker.common.error.ErrorCode;
import com.workouttracker.user.User;
import com.workouttracker.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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

    @Mock
    private RefreshTokenStore refreshTokenStore;

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

    // ====================================================================
    // signup
    // ====================================================================

    @Test
    @DisplayName("회원가입 성공 - 이메일 중복 없으면 BCrypt 해싱 후 저장")
    void signup_success() {
        SignupRequest request = new SignupRequest("new@example.com", "Secret1234", "neo");
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret1234")).thenReturn("$2a$10$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 42L);
            return u;
        });

        SignupResponse response = authService.signup(request);

        assertThat(response.userId()).isEqualTo(42L);
        assertThat(response.email()).isEqualTo("new@example.com");
        assertThat(response.nickname()).isEqualTo("neo");
        verify(passwordEncoder, times(1)).encode("Secret1234");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 중복 시 EMAIL_DUPLICATED")
    void signup_emailDuplicated() {
        SignupRequest request = new SignupRequest("kim@example.com", "Secret1234", "kim");
        when(userRepository.existsByEmail("kim@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_DUPLICATED);

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    // ====================================================================
    // login
    // ====================================================================

    @Test
    @DisplayName("로그인 성공 - Access + Refresh 둘 다 발급되고 store 에 저장된다")
    void login_success_issuesBothTokensAndPersistsRefresh() {
        LoginRequest request = new LoginRequest("kim@example.com", "Secret1234");
        when(userRepository.findByEmail("kim@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("Secret1234", existingUser.getPasswordHash())).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(1L, "kim@example.com")).thenReturn("access-jwt");
        when(jwtTokenProvider.generateRefreshToken(eq(1L), anyString())).thenReturn("refresh-jwt");
        when(jwtTokenProvider.getAccessExpiresInSeconds()).thenReturn(900L);
        when(jwtTokenProvider.getRefreshExpiresInSeconds()).thenReturn(1209600L);

        LoginResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-jwt");
        assertThat(response.refreshToken()).isEqualTo("refresh-jwt");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);
        assertThat(response.refreshExpiresIn()).isEqualTo(1209600L);

        ArgumentCaptor<String> jtiCaptor = ArgumentCaptor.forClass(String.class);
        verify(refreshTokenStore, times(1)).save(eq(1L), jtiCaptor.capture(), eq(1209600L));
        assertThat(jtiCaptor.getValue()).isNotBlank();
    }

    @Test
    @DisplayName("로그인 실패 - 이메일 없음 / 비밀번호 불일치 모두 INVALID_CREDENTIALS, 토큰 발급 없음")
    void login_invalidCredentials() {
        LoginRequest noEmailReq = new LoginRequest("nobody@example.com", "Secret1234");
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(noEmailReq))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        LoginRequest wrongPwReq = new LoginRequest("kim@example.com", "WrongPass1");
        when(userRepository.findByEmail("kim@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches(eq("WrongPass1"), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(wrongPwReq))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verify(jwtTokenProvider, never()).generateAccessToken(any(), anyString());
        verify(jwtTokenProvider, never()).generateRefreshToken(any(), anyString());
        verify(refreshTokenStore, never()).save(anyLong(), anyString(), anyLong());
    }

    // ====================================================================
    // refresh — rotation 성공 / reuse 탐지 / 만료 / 무효 시그니처
    // ====================================================================

    @Test
    @DisplayName("refresh 성공 - 옛 jti 제거 + 새 access/refresh 발급 + 새 jti 저장 (rotation)")
    void refresh_success_rotatesTokens() {
        Claims claims = claimsOf(1L, "old-jti");
        when(jwtTokenProvider.parseRefreshClaims("old-refresh")).thenReturn(claims);
        when(jwtTokenProvider.extractUserId(claims)).thenReturn(1L);
        when(refreshTokenStore.exists(1L, "old-jti")).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(jwtTokenProvider.generateAccessToken(1L, "kim@example.com")).thenReturn("new-access");
        when(jwtTokenProvider.generateRefreshToken(eq(1L), anyString())).thenReturn("new-refresh");
        when(jwtTokenProvider.getAccessExpiresInSeconds()).thenReturn(900L);
        when(jwtTokenProvider.getRefreshExpiresInSeconds()).thenReturn(1209600L);

        LoginResponse response = authService.refresh("old-refresh");

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        verify(refreshTokenStore, times(1)).delete(1L, "old-jti"); // 옛 토큰 즉시 제거
        ArgumentCaptor<String> newJti = ArgumentCaptor.forClass(String.class);
        verify(refreshTokenStore, times(1)).save(eq(1L), newJti.capture(), eq(1209600L));
        assertThat(newJti.getValue()).isNotBlank().isNotEqualTo("old-jti");
        verify(refreshTokenStore, never()).deleteAllByUser(anyLong());
    }

    @Test
    @DisplayName("refresh 재사용 - 시그니처 유효하나 store 에 없으면 REUSED + 전체 세션 무효화")
    void refresh_storeMiss_triggersReuseDefence() {
        Claims claims = claimsOf(1L, "stale-jti");
        when(jwtTokenProvider.parseRefreshClaims("stale-refresh")).thenReturn(claims);
        when(jwtTokenProvider.extractUserId(claims)).thenReturn(1L);
        when(refreshTokenStore.exists(1L, "stale-jti")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh("stale-refresh"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_REUSED);

        verify(refreshTokenStore, times(1)).deleteAllByUser(1L);
        verify(refreshTokenStore, never()).save(anyLong(), anyString(), anyLong());
        verify(jwtTokenProvider, never()).generateAccessToken(any(), anyString());
    }

    @Test
    @DisplayName("refresh 만료 - ExpiredJwtException 은 REFRESH_TOKEN_EXPIRED")
    void refresh_expired_throwsRefreshTokenExpired() {
        when(jwtTokenProvider.parseRefreshClaims("expired-refresh"))
                .thenThrow(new ExpiredJwtException(null, null, "expired"));

        assertThatThrownBy(() -> authService.refresh("expired-refresh"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_EXPIRED);

        verify(refreshTokenStore, never()).deleteAllByUser(anyLong());
    }

    @Test
    @DisplayName("refresh 시그니처 위변조 - JwtException 은 INVALID_REFRESH_TOKEN")
    void refresh_invalidSignature_throwsInvalidRefreshToken() {
        when(jwtTokenProvider.parseRefreshClaims("forged-refresh"))
                .thenThrow(new SignatureException("bad signature"));

        assertThatThrownBy(() -> authService.refresh("forged-refresh"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    // ====================================================================
    // logout / logoutAll
    // ====================================================================

    @Test
    @DisplayName("logout(userId, refreshToken) - 본인 토큰의 jti 만 무효화")
    void logout_singleSession() {
        Claims claims = claimsOf(1L, "my-jti");
        when(jwtTokenProvider.parseRefreshClaims("my-refresh")).thenReturn(claims);
        when(jwtTokenProvider.extractUserId(claims)).thenReturn(1L);

        authService.logout(1L, "my-refresh");

        verify(refreshTokenStore, times(1)).delete(1L, "my-jti");
        verify(refreshTokenStore, never()).deleteAllByUser(anyLong());
    }

    @Test
    @DisplayName("logout(userId, null) - refresh token 미제공 시 전체 세션 무효화")
    void logout_noToken_deletesAll() {
        authService.logout(1L, null);

        verify(refreshTokenStore, times(1)).deleteAllByUser(1L);
        verify(refreshTokenStore, never()).delete(anyLong(), anyString());
    }

    @Test
    @DisplayName("logout - 다른 사용자의 refresh 토큰 제출 시 그 jti 무효화 안 함")
    void logout_subjectMismatch_doesNothing() {
        Claims claims = claimsOf(99L, "someone-else-jti");
        when(jwtTokenProvider.parseRefreshClaims("other-refresh")).thenReturn(claims);
        when(jwtTokenProvider.extractUserId(claims)).thenReturn(99L);

        authService.logout(1L, "other-refresh");

        verify(refreshTokenStore, never()).delete(anyLong(), anyString());
        verify(refreshTokenStore, never()).deleteAllByUser(anyLong());
    }

    @Test
    @DisplayName("logoutAll - 해당 userId 의 모든 활성 jti 무효화")
    void logoutAll() {
        authService.logoutAll(1L);
        verify(refreshTokenStore, times(1)).deleteAllByUser(1L);
    }

    // ====================================================================
    // helpers
    // ====================================================================

    private Claims claimsOf(Long userId, String jti) {
        // Claims 는 인터페이스 — DefaultClaims (impl 모듈) 는 testCompile 에 없어서 mock 사용.
        // AuthService 내부에서 jwtTokenProvider.extractUserId(claims) 와 claims.getId() 만
        // 호출하므로 두 가지 stub 만 필요. (extractUserId 는 외부에서 별도 stub)
        Claims c = mock(Claims.class);
        when(c.getId()).thenReturn(jti);
        return c;
    }
}
