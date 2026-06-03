package com.workouttracker.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인/리프레시 응답 (Access + Refresh 토큰)")
public record LoginResponse(
        @Schema(description = "Access Token (JWT, HS256)", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,

        @Schema(description = "토큰 타입", example = "Bearer")
        String tokenType,

        @Schema(description = "Access Token 만료 시간(초)", example = "900")
        long expiresIn,

        @Schema(description = "Refresh Token (JWT, HS256). 회전(rotation) 시 새 값으로 교체됨.",
                example = "eyJhbGciOiJIUzI1NiJ9...")
        String refreshToken,

        @Schema(description = "Refresh Token 만료 시간(초)", example = "1209600")
        long refreshExpiresIn
) {
    public static LoginResponse bearer(String accessToken, long expiresIn,
                                       String refreshToken, long refreshExpiresIn) {
        return new LoginResponse(accessToken, "Bearer", expiresIn, refreshToken, refreshExpiresIn);
    }
}
