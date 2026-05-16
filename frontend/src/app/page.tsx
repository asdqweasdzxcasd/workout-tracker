"use client";

/**
 * 랜딩 페이지.
 *
 * <p>설계: docs/design.md 4.1 page.tsx 랜딩
 *
 * <p>로그인 여부에 따라:
 * <ul>
 *   <li>토큰 있음 → /sessions</li>
 *   <li>토큰 없음 → /login</li>
 * </ul>
 *
 * <p>SSR 단계에서는 localStorage 접근 불가하므로 useEffect 안에서 라우팅한다.
 */
import { useEffect } from "react";
import { useRouter } from "next/navigation";

import { getAccessToken } from "@/lib/auth-storage";

export default function Home() {
  const router = useRouter();

  useEffect(() => {
    const token = getAccessToken();
    router.replace(token ? "/sessions" : "/login");
  }, [router]);

  return (
    <main className="flex min-h-screen items-center justify-center">
      <p className="text-sm text-zinc-500">로딩 중...</p>
    </main>
  );
}
