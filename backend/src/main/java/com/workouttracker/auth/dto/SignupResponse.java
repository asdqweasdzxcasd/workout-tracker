package com.workouttracker.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원가입 응답")
public record SignupResponse(
        @Schema(description = "사용자 ID", example = "1") Long userId,
        @Schema(description = "이메일", example = "kim@example.com") String email,
        @Schema(description = "닉네임", example = "kim") String nickname,
        @Schema(description = "이메일 인증 여부 (가입 직후 false, 인증 완료 시 true)", example = "false")
        boolean emailVerified
) {}
