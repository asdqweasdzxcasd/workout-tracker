package com.workouttracker.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 소셜 인증 실패 후처리 (스펙: provider 가 오류를 반환 / state 불일치).
 *
 * <p>동의 거부, state 불일치·만료, code 교환 실패 등 모든 실패를 자체 토큰 발급 없이
 * 프론트 로그인 화면으로 돌려보낸다. 실패 상세는 서버 로그에만 남긴다
 * (URL 에 원인 노출 = provider 응답 내용 유출 여지 → error=oauth 고정).
 */
@Slf4j
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private final String failureRedirectUri;

    public OAuth2LoginFailureHandler(
            @Value("${app.oauth2.failure-redirect-uri}") String failureRedirectUri) {
        this.failureRedirectUri = failureRedirectUri;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        log.warn("소셜 로그인 실패: {}", exception.toString());
        response.sendRedirect(failureRedirectUri);
    }
}
