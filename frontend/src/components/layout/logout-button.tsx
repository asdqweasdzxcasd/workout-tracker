"use client";

/**
 * 로그아웃 버튼.
 *
 * <p>localStorage 의 access_token 제거 + React Query 캐시 무효화 + /login 이동.
 * 캐시를 비워서 다음 로그인 사용자가 이전 사용자의 데이터를 잠시라도 보지 않게 한다.
 */
import { useQueryClient } from "@tanstack/react-query";
import { LogOut } from "lucide-react";
import { useRouter } from "next/navigation";

import { Button } from "@/components/ui/button";
import { clearAccessToken } from "@/lib/auth-storage";

export function LogoutButton() {
  const router = useRouter();
  const queryClient = useQueryClient();

  const handleClick = () => {
    clearAccessToken();
    queryClient.clear();
    router.replace("/login");
  };

  return (
    <Button variant="ghost" onClick={handleClick} aria-label="로그아웃">
      <LogOut size={16} />
      <span>로그아웃</span>
    </Button>
  );
}
