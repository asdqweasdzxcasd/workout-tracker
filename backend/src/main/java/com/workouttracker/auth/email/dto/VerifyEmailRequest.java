package com.workouttracker.auth.email.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 이메일 인증 코드 검증 요청 DTO.
 *
 * <p>code 는 6자리 숫자(앞자리 0 포함 가능)만 허용한다.
 */
@Schema(description = "이메일 인증 코드 검증 요청")
public record VerifyEmailRequest(

        @Schema(description = "이메일", example = "kim@example.com")
        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @Schema(description = "6자리 인증 코드", example = "012345")
        @NotBlank
        @Pattern(regexp = "^\\d{6}$", message = "인증 코드는 6자리 숫자여야 합니다.")
        String code
) {}
