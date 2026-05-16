"use client";

/**
 * 클라이언트 인증 가드 hook.
 *
 * <p>설계: docs/design.md 4.4
 *
 * <p>Bearer + localStorage 방식이라 proxy(구 middleware) 에서 토큰을 읽을 수 없다.
 * 보호 영역의 layout 에서 본 hook 을 사용해 미인증 시 /login 으로 리다이렉트한다.
 *
 * <p>checked=false 인 동안에는 children 렌더링을 보류해서
 * "잠깐 깜빡 보이고 사라지는" 인증 정보 노출을 방지한다.
 *
 * <p>구현 노트:
 * <ul>
 *   <li>useSyncExternalStore 를 사용해 SSR/CSR 일관성을 가져간다.</li>
 *   <li>SSR snapshot 은 항상 false (token 없음) → 클라이언트 마운트 후 실제 값으로 동기화.</li>
 *   <li>setState 를 useEffect 안에서 직접 호출하지 않아 ESLint rule 충돌 없음.</li>
 * </ul>
 */
import { useEffect, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";

import { getAccessToken } from "./auth-storage";

// 모듈 상수 - 매 render 마다 새 함수 객체가 생기지 않도록 외부에 둔다.
function subscribeStorage(callback: () => void): () => void {
  if (typeof window === "undefined") return () => {};
  window.addEventListener("storage", callback);
  return () => window.removeEventListener("storage", callback);
}

function getClientSnapshot(): boolean {
  return getAccessToken() !== null;
}

function getServerSnapshot(): boolean {
  // SSR 단계: 토큰 알 수 없음 → 일관되게 false 반환 (hydration mismatch 방지)
  return false;
}

export function useAuthGuard(): boolean {
  const router = useRouter();
  const hasToken = useSyncExternalStore(subscribeStorage, getClientSnapshot, getServerSnapshot);

  useEffect(() => {
    if (!hasToken) {
      router.replace("/login");
    }
  }, [hasToken, router]);

  return hasToken;
}
