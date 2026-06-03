"use client";

/**
 * 로그아웃 버튼.
 *
 * <p>흐름:
 * <ol>
 *   <li>POST /auth/logout (refreshToken 포함) — 서버측 Redis 에서 해당 jti 무효화</li>
 *   <li>localStorage 의 access/refresh 토큰 제거 (logout() 내부 처리)</li>
 *   <li>React Query 캐시 무효화 — 다음 로그인 사용자가 이전 사용자 데이터 안 보게</li>
 *   <li>/login 으로 이동</li>
 * </ol>
 *
 * <p>네트워크 실패해도 로컬 정리 + 리다이렉트는 진행 (UX 우선).
 */
import { useQueryClient } from "@tanstack/react-query";
import { LogOut } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { logout } from "@/features/auth/api";

export function LogoutButton() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [pending, setPending] = useState(false);

  const handleClick = async () => {
    if (pending) return;
    setPending(true);
    await logout(); // 내부에서 try/finally 로 토큰 정리 보장
    queryClient.clear();
    router.replace("/login");
  };

  return (
    <Button variant="ghost" onClick={handleClick} disabled={pending} aria-label="로그아웃">
      <LogOut size={16} />
      <span>로그아웃</span>
    </Button>
  );
}
