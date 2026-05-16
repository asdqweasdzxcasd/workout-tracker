package com.workouttracker.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원가입 응답")
public record SignupResponse(
        @Schema(description = "사용자 ID", example = "1") Long userId,
        @Schema(description = "이메일", example = "kim@example.com") String email,
        @Schema(description = "닉네임", example = "kim") String nickname
) {}
