package com.workouttracker.auth.oauth;

import com.workouttracker.user.User;
import com.workouttracker.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * 소셜 신원 → 서비스 사용자 매핑 (설계 4: 계정 식별·연동 순서).
 *
 * <ol>
 *   <li>(provider, providerId) 조회 → 있으면 그 사용자</li>
 *   <li>없고 <b>검증된 이메일</b>이 있으면 같은 이메일 사용자에 provider 연동
 *       (extractor 가 미검증 이메일을 이미 걸렀으므로 여기 도달한 이메일은 신뢰)</li>
 *   <li>둘 다 없으면 신규 생성 — password_hash NULL, 이메일 인증됨(D.2 스킵)</li>
 * </ol>
 *
 * <p>전 과정 단일 트랜잭션. 동시 콜백 race 는 DB UNIQUE(provider, provider_id) 가 최종 방어.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthUserProvisioningService {

    private final UserRepository userRepository;

    @Transactional
    public User provision(OAuthUserInfo info) {
        // (1) 이미 이 소셜 계정으로 가입/연동된 사용자
        return userRepository.findByProviderAndProviderId(info.provider(), info.providerId())
                .map(user -> {
                    log.info("소셜 재로그인: userId={} provider={}", user.getId(), info.provider());
                    return user;
                })
                // (2) 같은 이메일 기존 계정에 연동 (이메일 없으면 스킵 — 카카오 선택동의)
                .or(() -> info.email().flatMap(email ->
                        userRepository.findByEmail(email).map(user -> {
                            user.linkSocial(info.provider(), info.providerId());
                            log.info("소셜 계정 연동: userId={} provider={} (이메일 일치)",
                                    user.getId(), info.provider());
                            return user;
                        })))
                // (3) 신규 소셜 가입
                .orElseGet(() -> {
                    User created = userRepository.save(User.ofSocial(
                            info.provider(),
                            info.providerId(),
                            info.email().orElse(null),
                            info.nickname().orElseGet(() -> fallbackNickname(info))));
                    log.info("소셜 신규 가입: userId={} provider={} emailPresent={}",
                            created.getId(), info.provider(), info.email().isPresent());
                    return created;
                });
    }

    /** 닉네임 미제공 시 기본값 — 예: "kakao_a1b2c3" (DDL varchar(50) 이내). */
    private String fallbackNickname(OAuthUserInfo info) {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        return info.provider().name().toLowerCase(Locale.ROOT) + "_" + suffix;
    }
}
