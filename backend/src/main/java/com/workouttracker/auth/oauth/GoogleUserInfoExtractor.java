package com.workouttracker.auth.oauth;

import com.workouttracker.user.AuthProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 구글 OIDC UserInfo 정규화 (스펙: 구글 OIDC 정규화).
 *
 * <p>구글은 표준 OIDC 라 attributes 가 평평한 구조다:
 * {@code sub}(고유 ID), {@code email}, {@code email_verified}, {@code name}.
 * email_verified=false 인 이메일은 계정 자동연동에 쓰면 안 되므로 버린다(설계 리스크: 계정 탈취).
 */
@Component
public class GoogleUserInfoExtractor implements OAuthUserInfoExtractor {

    @Override
    public AuthProvider provider() {
        return AuthProvider.GOOGLE;
    }

    @Override
    public String registrationId() {
        return "google";
    }

    @Override
    public OAuthUserInfo extract(Map<String, Object> attributes) {
        String sub = (String) attributes.get("sub");
        if (sub == null || sub.isBlank()) {
            throw new IllegalStateException("구글 UserInfo 에 sub 가 없음");
        }

        // email_verified 가 명시적으로 false 면 이메일을 신뢰하지 않는다 (자동 연동 방지)
        boolean emailVerified = Boolean.TRUE.equals(attributes.get("email_verified"));
        Optional<String> email = emailVerified
                ? Optional.ofNullable((String) attributes.get("email"))
                : Optional.empty();

        return new OAuthUserInfo(
                AuthProvider.GOOGLE,
                sub,
                email,
                Optional.ofNullable((String) attributes.get("name"))
        );
    }
}
