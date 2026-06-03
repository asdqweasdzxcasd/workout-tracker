package com.workouttracker.auth;

import com.workouttracker.auth.dto.LoginRequest;
import com.workouttracker.auth.dto.LoginResponse;
import com.workouttracker.auth.dto.MeResponse;
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
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 인증 도메인 서비스.
 *
 * <ul>
 *   <li>회원가입: 이메일 중복 검사 → 비밀번호 BCrypt → INSERT (단일 트랜잭션)</li>
 *   <li>로그인: 비밀번호 검증 → Access + Refresh 발급 → Refresh 를 {@link RefreshTokenStore} 에 저장</li>
 *   <li>리프레시: Refresh 검증 → rotation (옛 jti 제거 + 새 jti 발급/저장).
 *       옛 jti 가 store 에 없는데 시그니처가 유효 = 재사용 의심 → 그 사용자 모든 세션 무효화</li>
 *   <li>로그아웃: 특정 jti 무효화 (refreshToken 제공 시) 또는 전체 무효화</li>
 *   <li>전체 로그아웃: userId 의 모든 활성 jti 무효화</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        // 동시 가입 race condition 은 DB unique 제약이 최종 방어선.
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .build();

        User saved = userRepository.save(user);
        log.info("회원가입 완료: userId={} email={}", saved.getId(), saved.getEmail());
        return new SignupResponse(saved.getId(), saved.getEmail(), saved.getNickname());
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        return issueTokens(user);
    }

    /**
     * Refresh Token 으로 새 Access + Refresh 발급 (rotation).
     *
     * <p>옛 jti 가 store 에 없으면 두 가지 가능성:</p>
     * <ol>
     *   <li>이미 rotation 으로 삭제됨 (옛 토큰 재사용 시도) — 도난 가능성 → 전체 세션 무효화</li>
     *   <li>logout 으로 명시적 삭제됨 → 동일하게 도난 가정으로 처리 (강한 정책)</li>
     * </ol>
     */
    @Transactional(readOnly = true)
    public LoginResponse refresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwtTokenProvider.parseRefreshClaims(refreshToken);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long userId = jwtTokenProvider.extractUserId(claims);
        String oldJti = claims.getId();

        if (oldJti == null || !refreshTokenStore.exists(userId, oldJti)) {
            // 시그니처는 유효한데 store 에 없음 → 재사용 의심. 강한 방어로 전체 세션 무효화.
            log.warn("Refresh 재사용 감지 → 전체 세션 무효화: userId={} jti={}", userId, oldJti);
            refreshTokenStore.deleteAllByUser(userId);
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_REUSED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        // rotation: 옛 jti 즉시 제거 + 새 토큰 발급/저장
        refreshTokenStore.delete(userId, oldJti);
        log.info("Refresh rotation: userId={} oldJti={}", userId, oldJti);

        return issueTokens(user);
    }

    /**
     * 로그아웃. {@code refreshToken} 이 제공되면 해당 jti 만 무효화,
     * null 이면 사용자 전체 세션 무효화(보수적).
     */
    public void logout(Long userId, String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            refreshTokenStore.deleteAllByUser(userId);
            log.info("Logout (all sessions, no refresh token provided): userId={}", userId);
            return;
        }
        try {
            Claims claims = jwtTokenProvider.parseRefreshClaims(refreshToken);
            Long tokenUserId = jwtTokenProvider.extractUserId(claims);
            String jti = claims.getId();
            // 인증된 사용자(userId) 와 토큰의 subject 가 일치하는 경우에만 그 jti 만 무효화.
            // 불일치하면 무시 (다른 사용자의 토큰을 끄지 못하게).
            if (tokenUserId.equals(userId) && jti != null) {
                refreshTokenStore.delete(userId, jti);
                log.info("Logout (single session): userId={} jti={}", userId, jti);
            } else {
                log.warn("Logout 요청의 refresh token subject 불일치: authUserId={} tokenUserId={}",
                        userId, tokenUserId);
            }
        } catch (JwtException | IllegalArgumentException e) {
            // 형식이 깨졌거나 만료된 refresh 토큰이면, 보수적으로 전체 무효화.
            refreshTokenStore.deleteAllByUser(userId);
            log.info("Logout (all sessions, invalid refresh token): userId={}", userId);
        }
    }

    /** 전체 기기 로그아웃 — 해당 userId 의 모든 활성 jti 무효화. */
    public void logoutAll(Long userId) {
        refreshTokenStore.deleteAllByUser(userId);
        log.info("Logout all: userId={}", userId);
    }

    @Transactional(readOnly = true)
    public MeResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return new MeResponse(user.getId(), user.getEmail(), user.getNickname());
    }

    /** 공통: 새 Access + Refresh 발급 + Refresh 를 store 에 저장. */
    private LoginResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
        String jti = UUID.randomUUID().toString();
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), jti);
        refreshTokenStore.save(user.getId(), jti, jwtTokenProvider.getRefreshExpiresInSeconds());

        log.info("토큰 발급: userId={} jti={}", user.getId(), jti);
        return LoginResponse.bearer(
                accessToken,
                jwtTokenProvider.getAccessExpiresInSeconds(),
                refreshToken,
                jwtTokenProvider.getRefreshExpiresInSeconds()
        );
    }
}
