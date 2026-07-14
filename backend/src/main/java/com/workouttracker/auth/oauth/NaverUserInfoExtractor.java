package com.workouttracker.auth.oauth;

import com.workouttracker.user.AuthProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 네이버 UserInfo 정규화 (스펙: 네이버/카카오 UserInfo 정규화).
 *
 * <p>네이버 응답은 실제 데이터가 {@code response} 객체에 중첩돼 있다:
 * <pre>{ "resultcode": "00", "message": "success",
 *   "response": { "id": "...", "email": "...", "nickname": "..." } }</pre>
 * application.yml 의 {@code user-name-attribute: response} 와 짝을 이룬다.
 * 네이버 콘솔 '제공 정보'에서 이메일·별명을 체크했으므로 보통 존재하지만,
 * 사용자가 동의 화면에서 거부할 수 있어 Optional 로 다룬다.
 */
@Component
public class NaverUserInfoExtractor implements OAuthUserInfoExtractor {

    @Override
    public AuthProvider provider() {
        return AuthProvider.NAVER;
    }

    @Override
    public String registrationId() {
        return "naver";
    }

    @Override
    @SuppressWarnings("unchecked")
    public OAuthUserInfo extract(Map<String, Object> attributes) {
        // user-name-attribute=response 라 attributes 자체가 response 맵일 수도,
        // 원본 전체일 수도 있다(호출 경로에 따라). 둘 다 흡수한다.
        Map<String, Object> response = attributes.containsKey("response")
                ? (Map<String, Object>) attributes.get("response")
                : attributes;

        String id = response.get("id") == null ? null : String.valueOf(response.get("id"));
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("네이버 UserInfo 에 response.id 가 없음");
        }

        return new OAuthUserInfo(
                AuthProvider.NAVER,
                id,
                Optional.ofNullable((String) response.get("email")),
                Optional.ofNullable((String) response.get("nickname"))
        );
    }
}
