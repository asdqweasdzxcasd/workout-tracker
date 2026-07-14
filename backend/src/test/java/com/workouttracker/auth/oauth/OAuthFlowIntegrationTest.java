package com.workouttracker.auth.oauth;

import com.workouttracker.auth.token.RefreshTokenStore;
import com.workouttracker.user.User;
import com.workouttracker.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OAuth 흐름 통합 테스트 (스펙: 로그인 시작 / 콜백 state 검증 / 자체 JWT 발급).
 *
 * <p>실제 provider 호출 없이 검증 가능한 구간만 다룬다:
 * authorize 리다이렉트, 미지원 provider 400, state 불일치 → 실패 리다이렉트,
 * exchange code → 자체 토큰 발급 / 무효 code 401.
 * Redis 의존부(state 저장·exchange 저장·refresh 저장)는 mock — Redis 자체 동작은
 * Testcontainers 통합 테스트(CI)에서 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("OAuth 소셜 로그인 흐름")
class OAuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private RedisOAuth2AuthorizationRequestRepository authorizationRequestRepository;

    @MockBean
    private OAuthExchangeCodeStore exchangeCodeStore;

    @MockBean
    private RefreshTokenStore refreshTokenStore;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("지원 provider 로그인 시작 → provider authorization URL 로 302 (state 포함)")
    void authorizeRedirectsToProvider() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("accounts.google.com")))
                .andExpect(header().string("Location", containsString("state=")))
                .andExpect(header().string("Location", containsString("redirect_uri=")));
    }

    @Test
    @DisplayName("카카오/네이버 커스텀 provider 도 authorization URL 로 302")
    void customProvidersRedirect() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/kakao"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("kauth.kakao.com")));

        mockMvc.perform(get("/oauth2/authorization/naver"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("nid.naver.com")));
    }

    @Test
    @DisplayName("미지원 provider 는 400 (스펙: 미지원 provider 요청 거부)")
    void unsupportedProviderReturns400() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/facebook"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("콜백 state 가 저장소에 없으면(불일치/만료) 토큰 교환 없이 실패 리다이렉트")
    void invalidStateFailsToFailureRedirect() throws Exception {
        // 저장소가 state 를 모른다 = 불일치/만료 (mock 이 null 반환)
        when(authorizationRequestRepository.removeAuthorizationRequest(any(), any()))
                .thenReturn(null);
        when(authorizationRequestRepository.loadAuthorizationRequest(any())).thenReturn(null);

        mockMvc.perform(get("/login/oauth2/code/google")
                        .param("code", "provider-code")
                        .param("state", "forged-or-expired-state"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("error=oauth")));
    }

    @Test
    @DisplayName("provider 가 error 로 콜백하면 실패 리다이렉트 (동의 거부 등)")
    void providerErrorCallbackFails() throws Exception {
        mockMvc.perform(get("/login/oauth2/code/google")
                        .param("error", "access_denied")
                        .param("state", "whatever"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("error=oauth")));
    }

    @Test
    @DisplayName("유효한 exchange code → 자체 Access/Refresh 토큰 발급 (스펙: 자체 JWT 발급)")
    void exchangeIssuesOwnTokens() throws Exception {
        User social = userRepository.save(
                User.ofSocial(com.workouttracker.user.AuthProvider.GOOGLE, "sub-1", "u@gmail.com", "u"));
        when(exchangeCodeStore.consume("valid-code")).thenReturn(Optional.of(social.getId()));

        mockMvc.perform(post("/api/v1/auth/oauth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"valid-code\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("무효/재사용 exchange code 는 401")
    void invalidExchangeCodeReturns401() throws Exception {
        when(exchangeCodeStore.consume("used-or-expired")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/auth/oauth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"used-or-expired\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("code 누락은 400 (입력 검증)")
    void blankCodeReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/oauth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
