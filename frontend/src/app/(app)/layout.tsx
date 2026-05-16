"use client";

/**
 * 인증 필요 영역 레이아웃.
 *
 * <p>설계: docs/design.md 4.4 인증 가드
 *
 * <p>useAuthGuard 가 토큰 검사 + 미인증 시 /login 리다이렉트.
 * checked 가 false 인 동안에는 children 을 렌더링하지 않아 보호 컨텐츠가 깜빡 보이지 않게 한다.
 */
import { Header } from "@/components/layout/header";
import { useAuthGuard } from "@/lib/use-auth";

export default function AppLayout({ children }: { children: React.ReactNode }) {
  const checked = useAuthGuard();

  if (!checked) {
    // 인증 확인 중 - 깜빡임 방지를 위해 빈 화면 (필요 시 스피너 추가 가능)
    return null;
  }

  return (
    <>
      <Header />
      <main className="mx-auto w-full max-w-3xl flex-1 px-4 py-6">{children}</main>
    </>
  );
}
