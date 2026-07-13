package com.workouttracker.auth.oauth;

import com.workouttracker.user.AuthProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 카카오 UserInfo 정규화 (스펙: 네이버/카카오 UserInfo 정규화).
 *
 * <p>카카오 응답 구조:
 * <pre>{ "id": 123456789,
 *   "kakao_account": { "email": "...", "is_email_verified": true,
 *     "profile": { "nickname": "..." } },
 *   "properties": { "nickname": "..." } }</pre>
 *
 * <p>주의(설계): 이메일은 동의항목이 비즈 앱 전환 후에만 활성화되는 <b>선택 제공</b>이라
 * 없을 수 있다. 이메일이 있어도 {@code is_email_verified=false} 면 자동 연동에 쓰지 않는다.
 */
@Component
public class KakaoUserInfoExtractor implements OAuthUserInfoExtractor {

    @Override
    public AuthProvider provider() {
        return AuthProvider.KAKAO;
    }

    @Override
    public String registrationId() {
        return "kakao";
    }

    @Override
    @SuppressWarnings("unchecked")
    public OAuthUserInfo extract(Map<String, Object> attributes) {
        Object rawId = attributes.get("id");
        if (rawId == null) {
            throw new IllegalStateException("카카오 UserInfo 에 id 가 없음");
        }
        String id = String.valueOf(rawId); // 카카오 id 는 숫자(Long) → 문자열 통일

        Map<String, Object> account = (Map<String, Object>) attributes
                .getOrDefault("kakao_account", Map.of());

        // 이메일: 존재 + 카카오가 검증한 경우에만 신뢰 (없으면 provider_id 로만 식별)
        Optional<String> email = Optional.ofNullable((String) account.get("email"))
                .filter(e -> Boolean.TRUE.equals(account.get("is_email_verified")));

        // 닉네임: kakao_account.profile.nickname 우선, 없으면 properties.nickname
        Map<String, Object> profile = (Map<String, Object>) account
                .getOrDefault("profile", Map.of());
        Map<String, Object> properties = (Map<String, Object>) attributes
                .getOrDefault("properties", Map.of());
        Optional<String> nickname = Optional.ofNullable((String) profile.get("nickname"))
                .or(() -> Optional.ofNullable((String) properties.get("nickname")));

        return new OAuthUserInfo(AuthProvider.KAKAO, id, email, nickname);
    }
}
