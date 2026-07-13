package com.workouttracker.auth.oauth;

import com.workouttracker.auth.AuthService;
import com.workouttracker.auth.dto.LoginResponse;
import com.workouttracker.auth.oauth.dto.OAuthExchangeRequest;
import com.workouttracker.common.error.BusinessException;
import com.workouttracker.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * OAuth 소셜 로그인 보조 엔드포인트 (D.3).
 *
 * <ul>
 *   <li>POST /api/v1/auth/oauth/exchange — 1회용 code → 자체 Access/Refresh 토큰</li>
 *   <li>GET /oauth2/authorization/{registrationId} — <b>미지원 provider fallback.</b>
 *       지원 provider 는 이 컨트롤러에 도달하기 전에
 *       OAuth2AuthorizationRequestRedirectFilter 가 가로채 302 리다이렉트한다.
 *       여기 도달했다 = 등록 안 된 provider → 400 (스펙: 미지원 provider 요청 거부)</li>
 * </ul>
 */
@Tag(name = "OAuth", description = "소셜 로그인 (구글/네이버/카카오)")
@RestController
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthExchangeCodeStore exchangeCodeStore;
    private final AuthService authService;

    @Operation(summary = "소셜 로그인 코드 교환",
            description = "소셜 로그인 성공 시 받은 1회용 code 를 자체 Access/Refresh 토큰으로 교환한다. "
                    + "code 는 60초 유효, 1회만 사용 가능.")
    @PostMapping("/api/v1/auth/oauth/exchange")
    public LoginResponse exchange(@Valid @RequestBody OAuthExchangeRequest request) {
        Long userId = exchangeCodeStore.consume(request.code())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OAUTH_EXCHANGE_CODE));
        return authService.issueTokensForUser(userId);
    }

    @Operation(hidden = true)
    @GetMapping("/oauth2/authorization/{registrationId}")
    public void unsupportedProvider(@PathVariable String registrationId) {
        throw new BusinessException(ErrorCode.UNSUPPORTED_OAUTH_PROVIDER);
    }
}
