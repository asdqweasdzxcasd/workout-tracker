package com.workouttracker.auth.email.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 이메일 인증 코드 재발송 요청 DTO.
 *
 * <p>이메일 열거(enumeration) 방어를 위해, 미가입/이미인증 이메일에도 동일하게 202 를 응답한다.
 * 실제 발송 여부만 내부적으로 조건부 결정한다.
 */
@Schema(description = "이메일 인증 코드 재발송 요청")
public record ResendVerificationRequest(

        @Schema(description = "이메일", example = "kim@example.com")
        @NotBlank
        @Email
        @Size(max = 255)
        String email
) {}
