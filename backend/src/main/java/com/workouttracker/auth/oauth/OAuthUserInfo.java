package com.workouttracker.auth.oauth;

import com.workouttracker.user.AuthProvider;

import java.util.Optional;

/**
 * provider 별 UserInfo 응답을 정규화한 공통 표현 (설계 2: 정규화 어댑터의 출력).
 *
 * @param provider   소셜 제공자
 * @param providerId provider 내 고유 사용자 ID (구글 sub / 네이버 response.id / 카카오 id)
 * @param email      제공자가 준 이메일 — 카카오는 동의항목 미설정 시 없음
 * @param nickname   표시 이름 (없으면 호출부에서 기본값 생성)
 */
public record OAuthUserInfo(
        AuthProvider provider,
        String providerId,
        Optional<String> email,
        Optional<String> nickname
) {
    public OAuthUserInfo {
        if (provider == null || providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("provider/providerId 는 필수");
        }
    }
}
