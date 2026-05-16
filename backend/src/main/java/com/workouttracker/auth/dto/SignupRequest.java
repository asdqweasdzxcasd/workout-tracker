package com.workouttracker.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청 DTO.
 *
 * <p>검증 규칙 (docs/design.md 3.5 POST /auth/signup):
 * <ul>
 *   <li>이메일: RFC 형식</li>
 *   <li>비밀번호: 8자 이상 + 영문 + 숫자 포함</li>
 *   <li>닉네임: 2~20자</li>
 * </ul>
 */
@Schema(description = "회원가입 요청")
public record SignupRequest(

        @Schema(description = "이메일", example = "kim@example.com")
        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @Schema(description = "비밀번호 (8자 이상, 영문+숫자 포함)", example = "Secret1234")
        @NotBlank
        @Size(min = 8, max = 72) // BCrypt 한계 72 bytes
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]{8,72}$",
                message = "비밀번호는 영문과 숫자를 모두 포함해 8자 이상이어야 합니다."
        )
        String password,

        @Schema(description = "닉네임 (2~20자)", example = "kim")
        @NotBlank
        @Size(min = 2, max = 20)
        String nickname
) {}
