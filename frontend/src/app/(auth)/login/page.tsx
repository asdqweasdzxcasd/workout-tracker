"use client";

/**
 * 로그인 페이지.
 *
 * <p>설계: docs/design.md 4.1 (auth)/login
 *
 * <p>흐름:
 * <ol>
 *   <li>react-hook-form + zod 로 클라이언트 측 즉시 검증</li>
 *   <li>POST /auth/login → accessToken 저장</li>
 *   <li>?next= 쿼리스트링이 있으면 그곳으로, 없으면 /sessions 로 이동</li>
 *   <li>실패 시 백엔드 ErrorResponse.message 노출</li>
 * </ol>
 *
 * <p>Next.js 16: useSearchParams 는 CSR bailout 대상이라
 * 페이지 자체가 <Suspense> 로 감싸진 자식 컴포넌트에서 사용해야 build 통과.
 */
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { useForm } from "react-hook-form";

import { Button } from "@/components/ui/button";
import { FieldError, Input, Label } from "@/components/ui/input";
import { login } from "@/features/auth/api";
import { loginSchema, type LoginFormValues } from "@/features/auth/schemas";
import { extractErrorMessage } from "@/lib/api";
import { setAccessToken } from "@/lib/auth-storage";

export default function LoginPage() {
  return (
    <Suspense fallback={<LoginShell />}>
      <LoginForm />
    </Suspense>
  );
}

function LoginShell({ children }: { children?: React.ReactNode }) {
  return (
    <div className="rounded-xl border border-zinc-200 bg-white p-8 shadow-sm">
      <h1 className="mb-1 text-2xl font-bold text-zinc-900">로그인</h1>
      <p className="mb-6 text-sm text-zinc-500">workout-tracker 에 오신 것을 환영합니다.</p>
      {children}
    </div>
  );
}

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });

  const loginMutation = useMutation({
    mutationFn: login,
    onSuccess: (data) => {
      setAccessToken(data.accessToken);
      const next = searchParams.get("next");
      router.replace(next && next.startsWith("/") ? next : "/sessions");
    },
    onError: (error) => {
      setServerError(extractErrorMessage(error, "로그인에 실패했습니다."));
    },
  });

  const onSubmit = (values: LoginFormValues) => {
    setServerError(null);
    loginMutation.mutate(values);
  };

  return (
    <LoginShell>
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
            autoComplete="current-password"
            placeholder="••••••••"
            aria-invalid={errors.password ? true : undefined}
            {...register("password")}
          />
          <FieldError message={errors.password?.message} />
        </div>

        {serverError ? (
          <div role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
            {serverError}
          </div>
        ) : null}

        <Button type="submit" fullWidth disabled={loginMutation.isPending}>
          {loginMutation.isPending ? "로그인 중..." : "로그인"}
        </Button>
      </form>

      <p className="mt-6 text-center text-sm text-zinc-600">
        아직 계정이 없나요?{" "}
        <Link href="/signup" className="font-medium text-blue-600 hover:underline">
          회원가입
        </Link>
      </p>
    </LoginShell>
  );
}
