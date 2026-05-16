"use client";

/**
 * 공통 헤더 - 로고/네비/로그아웃 버튼.
 *
 * <p>모바일 우선이므로 좁은 화면에서도 한 줄에 정리되도록 디자인.
 */
import { Dumbbell } from "lucide-react";
import Link from "next/link";

import { LogoutButton } from "./logout-button";

export function Header() {
  return (
    <header className="sticky top-0 z-10 border-b border-zinc-200 bg-white/90 backdrop-blur">
      <div className="mx-auto flex h-14 max-w-3xl items-center justify-between px-4">
        <Link href="/sessions" className="flex items-center gap-2 text-zinc-900">
          <Dumbbell size={20} className="text-blue-600" />
          <span className="text-base font-bold tracking-tight">workout-tracker</span>
        </Link>
        <LogoutButton />
      </div>
    </header>
  );
}
