"use client";

/**
 * 이메일 인증 페이지.
 *
 * <p>설계: docs/design.md 부록 D.2 (이메일 인증) 프론트엔드 흐름
 *
 * <p>흐름:
 * <ol>
 *   <li>가입 직후 signup 페이지가 <code>/verify-email?email=...</code> 로 보낸다.</li>
 *   <li>단일 input 에 6자리 코드 입력 → POST /auth/verify-email.</li>
 *   <li>성공 → <code>/login?verified=1</code> 로 이동(자동 로그인 X, 비번 재입력 유도).</li>
 *   <li>재발송 버튼: 202 면 60초 쿨다운 시작, 429 면 Retry-After(없으면 60초).</li>
 * </ol>
 *
 * <p>에러 UX 는 백엔드 한국어 메시지를 그대로 노출하되, <b>상태 전환</b>(재발송 강조/입력 비활성)만
 * ErrorResponse.code 로 분기한다.
 *
 * <p>Next.js 16: useSearchParams 는 CSR bailout 대상이라 <Suspense> 로 감싼 자식에서 사용한다.
 */
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useCallback, useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";

import { Button } from "@/components/ui/button";
import { FieldError, Input, Label } from "@/components/ui/input";
import { resendVerification, verifyEmail } from "@/features/auth/api";
import { verifyEmailSchema, type VerifyEmailFormValues } from "@/features/auth/schemas";
import {
  extractErrorCode,
  extractErrorMessage,
  extractRetryAfterSeconds,
} from "@/lib/api";

/** 재발송 기본 쿨다운(초). 백엔드 60초 쿨다운과 동일. Retry-After 미제공 시 fallback. */
const DEFAULT_RESEND_COOLDOWN_SEC = 60;

export default function VerifyEmailPage() {
  return (
    <Suspense fallback={<VerifyEmailShell />}>
      <VerifyEmailForm />
    </Suspense>
  );
}

function VerifyEmailShell({ children }: { children?: React.ReactNode }) {
  return (
    <div className="rounded-xl border border-zinc-200 bg-white p-8 shadow-sm">
      <h1 className="mb-1 text-2xl font-bold text-zinc-900">이메일 인증</h1>
      <p className="mb-6 text-sm text-zinc-500">메일함으로 전송된 6자리 코드를 입력해주세요.</p>
      {children}
    </div>
  );
}

function VerifyEmailForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const emailFromQuery = searchParams.get("email") ?? "";
  const fromLogin = searchParams.get("from") === "login";

  const [serverError, setServerError] = useState<string | null>(null);
  /** 코드 입력 자체를 막아야 하는 상태(TOO_MANY_ATTEMPTS) — 재발송으로만 복구. */
  const [inputDisabled, setInputDisabled] = useState(false);
  /** 만료/시도초과 시 재발송 버튼을 시각적으로 강조. */
  const [emphasizeResend, setEmphasizeResend] = useState(false);
  /** 재발송 쿨다운 남은 초(0 이면 즉시 가능). */
  const [cooldown, setCooldown] = useState(0);
  /** 재발송 성공 안내 메시지. */
  const [resendNotice, setResendNotice] = useState<string | null>(null);

  const cooldownTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const {
    register,
    handleSubmit,
    getValues,
    formState: { errors },
  } = useForm<VerifyEmailFormValues>({
    resolver: zodResolver(verifyEmailSchema),
    // email/code 모두 폼이 소유한다. email 은 쿼리값으로 초기화하되, 쿼리에 없으면 fallback input 으로 입력.
    defaultValues: { email: emailFromQuery, code: "" },
  });

  /** 쿨다운 카운트다운 시작. 기존 타이머는 정리 후 재설정. */
  const startCooldown = useCallback((seconds: number) => {
    if (cooldownTimerRef.current) {
      clearInterval(cooldownTimerRef.current);
    }
    setCooldown(seconds);
    cooldownTimerRef.current = setInterval(() => {
      setCooldown((prev) => {
        if (prev <= 1) {
          if (cooldownTimerRef.current) clearInterval(cooldownTimerRef.current);
          cooldownTimerRef.current = null;
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
  }, []);

  // 언마운트 시 타이머 누수 방지.
  useEffect(() => {
    return () => {
      if (cooldownTimerRef.current) clearInterval(cooldownTimerRef.current);
    };
  }, []);

  const verifyMutation = useMutation({
    mutationFn: verifyEmail,
    onSuccess: (_data, variables) => {
      // 자동 로그인하지 않는다 — 로그인 화면에서 비번 재입력. verified=1 로 안내 배너 노출.
      router.replace(`/login?verified=1&email=${encodeURIComponent(variables.email)}`);
    },
    onError: (error) => {
      const code = extractErrorCode(error);
      // 메시지는 백엔드 한국어 응답을 그대로. 상태 전환만 code 로 분기.
      setServerError(extractErrorMessage(error, "인증에 실패했습니다."));
      setResendNotice(null);

      if (code === "TOO_MANY_ATTEMPTS") {
        // 코드 폐기됨 → 입력 비활성 + 재발송 강조.
        setInputDisabled(true);
        setEmphasizeResend(true);
      } else if (code === "VERIFICATION_CODE_EXPIRED") {
        // 만료 → 재발송 강조(입력은 유지).
        setEmphasizeResend(true);
      } else {
        // INVALID_VERIFICATION_CODE 등 → 메시지만 노출, 재시도 허용.
        setEmphasizeResend(false);
      }
    },
  });

  const resendMutation = useMutation({
    mutationFn: resendVerification,
    onSuccess: () => {
      // 202 — 발송 접수. 입력/강조 상태를 초기화하고 60초 쿨다운 시작.
      setServerError(null);
      setInputDisabled(false);
      setEmphasizeResend(false);
      setResendNotice("인증 코드를 다시 보냈습니다. 메일함을 확인해주세요.");
      startCooldown(DEFAULT_RESEND_COOLDOWN_SEC);
    },
    onError: (error) => {
      const code = extractErrorCode(error);
      if (code === "RESEND_RATE_LIMITED") {
        // 429 — Retry-After 가 있으면 그 값, 없으면 60초 고정.
        const retryAfter = extractRetryAfterSeconds(error) ?? DEFAULT_RESEND_COOLDOWN_SEC;
        startCooldown(retryAfter);
      }
      setResendNotice(null);
      setServerError(extractErrorMessage(error, "재발송에 실패했습니다."));
    },
  });

  const onSubmit = (values: VerifyEmailFormValues) => {
    setServerError(null);
    setResendNotice(null);
    verifyMutation.mutate({ email: values.email, code: values.code });
  };

  const onResend = () => {
    if (cooldown > 0 || resendMutation.isPending) return;
    // email 은 폼이 소유한다(쿼리 prefill 또는 fallback 입력). 현재 값을 읽어 재발송 대상으로 사용.
    const email = getValues("email").trim();
    if (!email) {
      setServerError("이메일을 입력해주세요.");
      return;
    }
    setServerError(null);
    resendMutation.mutate({ email });
  };

  const resendLabel =
    cooldown > 0 ? `재발송 (${cooldown}초 후 가능)` : resendMutation.isPending ? "재발송 중..." : "인증 코드 재발송";

  return (
    <VerifyEmailShell>
      {fromLogin ? (
        <div className="mb-4 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800">
          로그인하려면 먼저 이메일 인증이 필요합니다.
        </div>
      ) : null}

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        {emailFromQuery ? (
          // 쿼리로 email 이 들어온 정상 경로 — 읽기 전용으로 보여준다(값은 폼이 소유).
          <div>
            <Label htmlFor="email">이메일</Label>
            <Input id="email" type="email" readOnly aria-readonly {...register("email")} />
          </div>
        ) : (
          // 쿼리에 email 이 없는 경우(직접 진입) — 입력 fallback.
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
        )}

        <div>
          <Label htmlFor="code" required>
            인증 코드
          </Label>
          <Input
            id="code"
            type="text"
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            placeholder="6자리 숫자"
            disabled={inputDisabled}
            aria-invalid={errors.code ? true : undefined}
            {...register("code")}
          />
          <FieldError message={errors.code?.message} />
        </div>

        {serverError ? (
          <div className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{serverError}</div>
        ) : null}

        {resendNotice ? (
          <div className="rounded-md bg-green-50 px-3 py-2 text-sm text-green-700">{resendNotice}</div>
        ) : null}

        <Button type="submit" fullWidth disabled={verifyMutation.isPending || inputDisabled}>
          {verifyMutation.isPending ? "확인 중..." : "인증 완료"}
        </Button>
      </form>

      <div className="mt-4">
        <Button
          type="button"
          variant={emphasizeResend ? "primary" : "secondary"}
          fullWidth
          onClick={onResend}
          disabled={cooldown > 0 || resendMutation.isPending}
        >
          {resendLabel}
        </Button>
      </div>

      <p className="mt-6 text-center text-sm text-zinc-600">
        이미 인증을 마쳤나요?{" "}
        <Link href="/login" className="font-medium text-blue-600 hover:underline">
          로그인
        </Link>
      </p>
    </VerifyEmailShell>
  );
}
