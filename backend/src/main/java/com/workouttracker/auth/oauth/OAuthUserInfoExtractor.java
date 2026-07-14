package com.workouttracker.auth.oauth;

import com.workouttracker.user.AuthProvider;

import java.util.Map;

/**
 * provider 별 UserInfo 응답 → {@link OAuthUserInfo} 정규화 어댑터 (설계 2).
 *
 * <p>새 provider 추가 = 이 인터페이스 구현체 1개 + application.yml registration 1개.
 * 콜백/프로비저닝/토큰 발급 흐름은 provider 를 모른다(스펙: Provider 확장성).
 */
public interface OAuthUserInfoExtractor {

    /** 이 어댑터가 담당하는 provider. */
    AuthProvider provider();

    /** Spring Security registrationId (application.yml 의 registration 키, 예: "google"). */
    String registrationId();

    /**
     * OAuth2User attributes → 공통 표현으로 변환.
     *
     * @param attributes Spring Security 가 UserInfo 엔드포인트에서 받아온 원본 속성 맵
     * @throws IllegalStateException 필수 필드(providerId)가 없을 때
     */
    OAuthUserInfo extract(Map<String, Object> attributes);
}
