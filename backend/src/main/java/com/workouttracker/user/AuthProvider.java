package com.workouttracker.user;

/**
 * 인증 제공자(소셜 로그인) 종류.
 *
 * <p>{@code null} = 자체(로컬) 가입 사용자. 그 외 값은 해당 소셜 제공자로 가입/연동된 사용자.
 * 새 제공자 추가 시 여기에 상수만 추가하면 되고, provider별 세부 처리는
 * {@code auth/oauth/} 어댑터에서 담당한다(설계: provider 확장성).
 */
public enum AuthProvider {
    GOOGLE,
    NAVER,
    KAKAO
}
