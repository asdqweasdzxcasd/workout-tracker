package com.workouttracker.config;

import com.workouttracker.auth.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
 * </ul>
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final Environment environment;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
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
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
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
