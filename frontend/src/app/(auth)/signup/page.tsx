"use client";

/**
 * 회원가입 페이지.
 *
 * <p>설계: docs/design.md 4.1 (auth)/signup
 *
 * <p>흐름:
 * <ol>
 *   <li>react-hook-form + zod 검증</li>
 *   <li>POST /auth/signup 성공 시 동일 폼 값으로 POST /auth/login 자동 호출</li>
 *   <li>accessToken 저장 후 /sessions 이동</li>
 *   <li>409 EMAIL_DUPLICATED 등 백엔드 메시지 그대로 노출</li>
 * </ol>
 */
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";

import { Button } from "@/components/ui/button";
import { FieldError, Input, Label } from "@/components/ui/input";
import { login, signup } from "@/features/auth/api";
import { signupSchema, type SignupFormValues } from "@/features/auth/schemas";
import { extractErrorMessage } from "@/lib/api";
import { setAccessToken } from "@/lib/auth-storage";

export default function SignupPage() {
  const router = useRouter();
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<SignupFormValues>({
    resolver: zodResolver(signupSchema),
    defaultValues: { email: "", password: "", nickname: "" },
  });

  const signupMutation = useMutation({
    mutationFn: async (values: SignupFormValues) => {
      await signup(values);
      // 회원가입 직후 동일 자격증명으로 로그인 - 사용자 경험 단축
      const loginResponse = await login({ email: values.email, password: values.password });
      return loginResponse;
    },
    onSuccess: (data) => {
      setAccessToken(data.accessToken);
      router.replace("/sessions");
    },
    onError: (error) => {
      setServerError(extractErrorMessage(error, "회원가입에 실패했습니다."));
    },
  });

  const onSubmit = (values: SignupFormValues) => {
    setServerError(null);
    signupMutation.mutate(values);
  };

  return (
    <div className="rounded-xl border border-zinc-200 bg-white p-8 shadow-sm">
      <h1 className="mb-1 text-2xl font-bold text-zinc-900">회원가입</h1>
      <p className="mb-6 text-sm text-zinc-500">이메일과 닉네임만 있으면 시작할 수 있습니다.</p>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        <div>
          <Label htmlFor="email" required>
            이메일
          </Label>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            placeholder="you@example.com"
            aria-invalid={errors.email ? true : undefined}
            {...register("email")}
          />
          <FieldError message={errors.email?.message} />
        </div>

        <div>
          <Label htmlFor="password" required>
            비밀번호
          </Label>
          <Input
            id="password"
            type="password"
            autoComplete="new-password"
            placeholder="영문 + 숫자 8자 이상"
            aria-invalid={errors.password ? true : undefined}
            {...register("password")}
          />
          <FieldError message={errors.password?.message} />
        </div>

        <div>
          <Label htmlFor="nickname" required>
            닉네임
          </Label>
          <Input
            id="nickname"
            type="text"
            autoComplete="nickname"
            placeholder="2~20자"
            aria-invalid={errors.nickname ? true : undefined}
            {...register("nickname")}
          />
          <FieldError message={errors.nickname?.message} />
        </div>

        {serverError ? (
          <div role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
            {serverError}
          </div>
        ) : null}

        <Button type="submit" fullWidth disabled={signupMutation.isPending}>
          {signupMutation.isPending ? "처리 중..." : "회원가입"}
        </Button>
      </form>

      <p className="mt-6 text-center text-sm text-zinc-600">
        이미 계정이 있나요?{" "}
        <Link href="/login" className="font-medium text-blue-600 hover:underline">
          로그인
        </Link>
      </p>
    </div>
  );
}
