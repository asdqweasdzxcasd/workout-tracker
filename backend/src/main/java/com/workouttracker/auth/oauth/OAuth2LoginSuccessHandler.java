package com.workouttracker.auth.oauth;

import com.workouttracker.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 소셜 인증 성공 후처리 (설계 3: exchange code 리다이렉트).
 *
 * <p>흐름: registrationId 로 extractor 선택 → UserInfo 정규화 → 프로비저닝(가입/연동)
 * → 1회용 exchange code 발급 → 프론트 콜백으로 {@code ?code=} 만 리다이렉트.
 * Access/Refresh 토큰은 절대 URL 에 싣지 않는다(스펙: BFF 경유 콜백 및 토큰 전달).
 * provider 의 access token 은 어디에도 저장하지 않는다(NoOp authorized client repository).
 */
@Slf4j
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final Map<String, OAuthUserInfoExtractor> extractorsByRegistrationId;
    private final OAuthUserProvisioningService provisioningService;
    private final OAuthExchangeCodeStore exchangeCodeStore;
    private final String successRedirectUri;

    public OAuth2LoginSuccessHandler(
            List<OAuthUserInfoExtractor> extractors,
            OAuthUserProvisioningService provisioningService,
            OAuthExchangeCodeStore exchangeCodeStore,
            @Value("${app.oauth2.success-redirect-uri}") String successRedirectUri) {
        this.extractorsByRegistrationId = extractors.stream()
                .collect(Collectors.toMap(OAuthUserInfoExtractor::registrationId, Function.identity()));
        this.provisioningService = provisioningService;
        this.exchangeCodeStore = exchangeCodeStore;
        this.successRedirectUri = successRedirectUri;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        String registrationId = token.getAuthorizedClientRegistrationId();

        OAuthUserInfoExtractor extractor = extractorsByRegistrationId.get(registrationId);
        if (extractor == null) {
            // registration 은 있는데 extractor 미구현 = 설정 실수. 명확히 실패시킨다.
            throw new IllegalStateException("extractor 미등록 provider: " + registrationId);
        }

        OAuth2User principal = token.getPrincipal();
        OAuthUserInfo info = extractor.extract(principal.getAttributes());
        User user = provisioningService.provision(info);

        String code = exchangeCodeStore.issue(user.getId());
        String redirectUrl = UriComponentsBuilder.fromUriString(successRedirectUri)
                .queryParam("code", code)
                .build().toUriString();

        log.info("소셜 로그인 성공: userId={} provider={}", user.getId(), info.provider());
        response.sendRedirect(redirectUrl);
    }
}
