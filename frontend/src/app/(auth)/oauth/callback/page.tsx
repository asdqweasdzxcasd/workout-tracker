"use client";

/**
 * OAuth 소셜 로그인 콜백 페이지 (D.3).
 *
 * <p>설계: openspec/changes/oauth-social-login design.md 결정 3.
 *
 * <p>흐름:
 * <ol>
 *   <li>백엔드 successHandler 가 1회용 exchange code 를 붙여 이 페이지로 리다이렉트</li>
 *   <li>code 를 BFF 경유 POST /auth/oauth/exchange 로 자체 토큰과 교환 (토큰은 URL 미노출)</li>
 *   <li>성공 → 토큰 저장 후 /sessions, 실패/누락 → /login?error=oauth</li>
 * </ol>
 *
 * <p>Next.js 16: useSearchParams 는 Suspense 하위에서만 사용 (CSR bailout).
 */
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef } from "react";

import { exchangeOAuthCode } from "@/features/auth/api";
import { setTokens } from "@/lib/auth-storage";

export default function OAuthCallbackPage() {
  return (
    <Suspense fallback={<CallbackShell />}>
      <OAuthCallbackHandler />
    </Suspense>
  );
}

function CallbackShell() {
  return (
    <div className="rounded-xl border border-zinc-200 bg-white p-8 text-center shadow-sm">
      <h1 className="mb-2 text-xl font-bold text-zinc-900">로그인 처리 중...</h1>
      <p className="text-sm text-zinc-500">잠시만 기다려주세요.</p>
    </div>
  );
}

function OAuthCallbackHandler() {
  const router = useRouter();
  const searchParams = useSearchParams();
  // React StrictMode 의 이중 실행으로 1회용 code 가 두 번 소비되는 것 방지
  const exchangedRef = useRef(false);

  useEffect(() => {
    if (exchangedRef.current) return;
    exchangedRef.current = true;

    const code = searchParams.get("code");
    if (!code) {
      router.replace("/login?error=oauth");
      return;
    }

    exchangeOAuthCode(code)
      .then((data) => {
        setTokens(data.accessToken, data.refreshToken);
        router.replace("/sessions");
      })
      .catch(() => {
        router.replace("/login?error=oauth");
      });
  }, [router, searchParams]);

  return <CallbackShell />;
}
