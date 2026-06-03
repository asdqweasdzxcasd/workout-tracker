package com.workouttracker.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Refresh Token 요청 (Access Token 갱신 또는 로그아웃 시 사용)")
public record RefreshRequest(
        @Schema(description = "Refresh Token (JWT)", example = "eyJhbGciOiJIUzI1NiJ9...")
        @NotBlank
        @Size(max = 1024)
        String refreshToken
) {
}
