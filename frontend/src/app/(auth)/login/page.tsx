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
import { login, oauthAuthorizeUrl, type OAuthProvider } from "@/features/auth/api";
import { loginSchema, type LoginFormValues } from "@/features/auth/schemas";
import { extractErrorCode, extractErrorMessage } from "@/lib/api";
import { setTokens } from "@/lib/auth-storage";

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

  // verify-email 성공 후 ?verified=1 로 돌아오면 안내 배너 노출. email 이 함께 오면 prefill.
  const justVerified = searchParams.get("verified") === "1";
  const emailFromQuery = searchParams.get("email") ?? "";
  // 소셜 로그인 실패(동의 거부, state 오류, code 만료) 시 ?error=oauth 로 돌아온다.
  const oauthFailed = searchParams.get("error") === "oauth";

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: emailFromQuery, password: "" },
  });

  const loginMutation = useMutation({
    mutationFn: login,
    onSuccess: (data) => {
      setTokens(data.accessToken, data.refreshToken);
      const next = searchParams.get("next");
      router.replace(next && next.startsWith("/") ? next : "/sessions");
    },
    onError: (error, variables) => {
      // 미인증 사용자 → 인증 페이지로. 상태 전환은 code 로만 분기.
      // variables 는 mutate 에 넘긴 LoginRequest — 제출한 email 을 그대로 전달.
      if (extractErrorCode(error) === "EMAIL_NOT_VERIFIED") {
        router.replace(`/verify-email?email=${encodeURIComponent(variables.email)}&from=login`);
        return;
      }
      setServerError(extractErrorMessage(error, "로그인에 실패했습니다."));
    },
  });

  const onSubmit = (values: LoginFormValues) => {
    setServerError(null);
    loginMutation.mutate(values);
  };

  return (
    <LoginShell>
      {justVerified ? (
        <div className="mb-4 rounded-md bg-green-50 px-3 py-2 text-sm text-green-700">
          이메일 인증이 완료되었습니다. 비밀번호로 로그인해주세요.
        </div>
      ) : null}

      {oauthFailed ? (
        <div role="alert" className="mb-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
          소셜 로그인에 실패했습니다. 다시 시도해주세요.
        </div>
      ) : null}

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

      <div className="mt-6">
        <div className="relative">
          <div className="absolute inset-0 flex items-center">
            <div className="w-full border-t border-zinc-200" />
          </div>
          <div className="relative flex justify-center text-xs">
            <span className="bg-white px-2 text-zinc-400">또는 소셜 계정으로</span>
          </div>
        </div>

        <div className="mt-4 space-y-2">
          <SocialLoginButton provider="google" label="구글로 계속하기" />
          <SocialLoginButton provider="naver" label="네이버로 계속하기" />
          <SocialLoginButton provider="kakao" label="카카오로 계속하기" />
        </div>
      </div>

      <p className="mt-6 text-center text-sm text-zinc-600">
        아직 계정이 없나요?{" "}
        <Link href="/signup" className="font-medium text-blue-600 hover:underline">
          회원가입
        </Link>
      </p>
    </LoginShell>
  );
}

/** 소셜 로그인 버튼 — 백엔드 authorize 진입점으로 전체 페이지 이동 (BFF 미경유). */
function SocialLoginButton({ provider, label }: { provider: OAuthProvider; label: string }) {
  const styles: Record<OAuthProvider, string> = {
    google: "border border-zinc-300 bg-white text-zinc-700 hover:bg-zinc-50",
    naver: "bg-[#03C75A] text-white hover:bg-[#02b351]",
    kakao: "bg-[#FEE500] text-zinc-900 hover:bg-[#f5dc00]",
  };
  return (
    <a
      href={oauthAuthorizeUrl(provider)}
      data-testid={`oauth-${provider}`}
      className={`flex w-full items-center justify-center rounded-md px-4 py-2.5 text-sm font-medium transition-colors ${styles[provider]}`}
    >
      {label}
    </a>
  );
}
