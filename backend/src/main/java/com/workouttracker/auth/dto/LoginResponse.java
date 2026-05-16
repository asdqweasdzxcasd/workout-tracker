package com.workouttracker.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 응답 (JWT)")
public record LoginResponse(
        @Schema(description = "Access Token (JWT)", example = "eyJhbGciOiJIUzI1NiJ9...") String accessToken,
        @Schema(description = "토큰 타입", example = "Bearer") String tokenType,
        @Schema(description = "만료 시간(초)", example = "3600") long expiresIn
) {
    public static LoginResponse bearer(String accessToken, long expiresIn) {
        return new LoginResponse(accessToken, "Bearer", expiresIn);
    }
}
