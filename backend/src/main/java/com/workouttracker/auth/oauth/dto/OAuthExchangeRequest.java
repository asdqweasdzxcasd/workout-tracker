package com.workouttracker.auth.oauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "소셜 로그인 exchange code → 자체 토큰 교환 요청")
public record OAuthExchangeRequest(
        @Schema(description = "소셜 로그인 성공 리다이렉트로 받은 1회용 코드")
        @NotBlank(message = "code 는 필수입니다.")
        String code
) {
}
