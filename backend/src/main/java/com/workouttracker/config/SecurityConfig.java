package com.workouttracker.config;

import com.workouttracker.auth.jwt.JwtAuthenticationFilter;
import com.workouttracker.auth.oauth.OAuth2LoginFailureHandler;
import com.workouttracker.auth.oauth.OAuth2LoginSuccessHandler;
import com.workouttracker.auth.oauth.RedisOAuth2AuthorizationRequestRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 설정.
 *
 * <ul>
 *   <li>CSRF disable, 세션 STATELESS</li>
 *   <li>공개 경로: /api/v1/auth/signup, /api/v1/auth/login, swagger, actuator/health</li>
 *   <li>그 외 모두 authenticated()</li>
 *   <li>JwtAuthenticationFilter 를 UsernamePasswordAuthenticationFilter 이전에 등록</li>
 *   <li>OAuth2 소셜 로그인(D.3): state 는 Redis 저장(STATELESS 대응), 성공 시
 *       exchange code 리다이렉트, provider 토큰은 저장하지 않음(NoOp repository)</li>
 * </ul>
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final Environment environment;
    private final RedisOAuth2AuthorizationRequestRepository authorizationRequestRepository;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;

    /**
     * 미등록 provider 를 필터 예외(500) 대신 "매칭 없음"(null) 으로 처리하는 resolver.
     * null 이면 redirect 필터가 체인을 계속 태워 {@code OAuthController} 의
     * fallback 에 도달 → 400 (스펙: 미지원 provider 요청 거부).
     */
    private static OAuth2AuthorizationRequestResolver tolerantAuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository) {
        DefaultOAuth2AuthorizationRequestResolver delegate =
                new DefaultOAuth2AuthorizationRequestResolver(
                        clientRegistrationRepository, "/oauth2/authorization");
        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                try {
                    return delegate.resolve(request);
                } catch (IllegalArgumentException e) {
                    return null; // 미등록 registrationId → MVC fallback 으로
                }
            }

            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
                try {
                    return delegate.resolve(request, clientRegistrationId);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            ClientRegistrationRepository clientRegistrationRepository) throws Exception {
        // E2E 테스트 지원 엔드포인트(/api/v1/test/**)는 prod 가 아닐 때만 공개한다.
        // prod 에서는 이 경로의 빈(TestSupportController) 자체가 없고, permitAll 도 추가되지 않아
        // 인증 대상으로만 남는다(평문 코드 노출 0 — 이중 방어).
        boolean isProd = environment.matchesProfiles("prod");

        http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                        auth
                                // 공개 경로
                                .requestMatchers(
                                        "/api/v1/auth/signup",
                                        "/api/v1/auth/login",
                                        "/api/v1/auth/refresh",
                                        "/api/v1/auth/verify-email",
                                        "/api/v1/auth/verify-email/resend",
                                        // OAuth 소셜 로그인 (D.3): 시작·콜백·코드교환은 미인증 상태에서 접근
                                        "/oauth2/authorization/**",
                                        "/login/oauth2/code/**",
                                        "/api/v1/auth/oauth/exchange",
                                        "/swagger-ui.html",
                                        "/swagger-ui/**",
                                        "/v3/api-docs",
                                        "/v3/api-docs/**",
                                        "/api-docs",
                                        "/api-docs/**",
                                        "/actuator/health"
                                ).permitAll();
                        // 테스트 지원 엔드포인트는 !prod 프로필에서만 공개.
                        if (!isProd) {
                                auth.requestMatchers("/api/v1/test/**").permitAll();
                        }
                        // 그 외 인증 필요
                        auth.anyRequest().authenticated();
                })
                .exceptionHandling(eh -> eh.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                // OAuth2 소셜 로그인 (설계 1: 백엔드 직접 콜백)
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(authz -> authz
                                .authorizationRequestResolver(
                                        tolerantAuthorizationRequestResolver(clientRegistrationRepository))
                                .authorizationRequestRepository(authorizationRequestRepository))
                        .successHandler(oAuth2LoginSuccessHandler)
                        .failureHandler(oAuth2LoginFailureHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * provider access token 을 어디에도 저장하지 않는다 (설계: 소셜 = 신원 확인만).
     * 기본 구현은 세션/메모리에 authorized client 를 남기는데, 우리는 성공 핸들러에서
     * 자체 JWT 를 발급하므로 provider 토큰을 보관할 이유가 없다.
     */
    @Bean
    public OAuth2AuthorizedClientRepository noOpAuthorizedClientRepository() {
        return new OAuth2AuthorizedClientRepository() {
            @Override
            public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
                    String clientRegistrationId, Authentication principal, HttpServletRequest request) {
                return null;
            }

            @Override
            public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient,
                                             Authentication principal, HttpServletRequest request,
                                             HttpServletResponse response) {
                // 의도적 no-op — provider 토큰 폐기
            }

            @Override
            public void removeAuthorizedClient(String clientRegistrationId, Authentication principal,
                                               HttpServletRequest request, HttpServletResponse response) {
                // no-op
            }
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // strength 10 - 설계 7.1 절
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
