/**
 * 인증 폼 zod 스키마.
 *
 * <p>백엔드(SignupRequest) 와 동일한 규칙을 두어 서버 왕복 전에 사용자에게 즉시 피드백한다.
 * <ul>
 *   <li>이메일: 형식 + 최대 255자</li>
 *   <li>비밀번호: 8~72자 + 영문/숫자 포함</li>
 *   <li>닉네임: 2~20자</li>
 * </ul>
 */
import { z } from "zod";

const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).{8,72}$/;

export const loginSchema = z.object({
  email: z.string().min(1, "이메일을 입력하세요.").email("이메일 형식이 올바르지 않습니다."),
  password: z.string().min(1, "비밀번호를 입력하세요."),
});
export type LoginFormValues = z.infer<typeof loginSchema>;

/** 이메일 인증 코드 — 백엔드(VerifyEmailRequest)와 동일하게 6자리 숫자. 앞자리 0 보존. */
const VERIFICATION_CODE_PATTERN = /^\d{6}$/;

export const verifyEmailSchema = z.object({
  email: z
    .string()
    .min(1, "이메일을 입력하세요.")
    .email("이메일 형식이 올바르지 않습니다.")
    .max(255, "이메일은 255자 이내여야 합니다."),
  code: z
    .string()
    .min(1, "인증 코드를 입력하세요.")
    .regex(VERIFICATION_CODE_PATTERN, "인증 코드는 6자리 숫자여야 합니다."),
});
export type VerifyEmailFormValues = z.infer<typeof verifyEmailSchema>;

export const signupSchema = z.object({
  email: z
    .string()
    .min(1, "이메일을 입력하세요.")
    .email("이메일 형식이 올바르지 않습니다.")
    .max(255, "이메일은 255자 이내여야 합니다."),
  password: z
    .string()
    .min(8, "비밀번호는 8자 이상이어야 합니다.")
    .max(72, "비밀번호는 72자 이내여야 합니다.")
    .regex(PASSWORD_PATTERN, "비밀번호는 영문과 숫자를 모두 포함해야 합니다."),
  nickname: z
    .string()
    .min(2, "닉네임은 2자 이상이어야 합니다.")
    .max(20, "닉네임은 20자 이내여야 합니다."),
});
export type SignupFormValues = z.infer<typeof signupSchema>;
