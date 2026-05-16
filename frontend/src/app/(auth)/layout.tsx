/**
 * 인증 화면용 레이아웃 (로그인/회원가입).
 *
 * <p>중앙 정렬 + 카드 디자인. 로고/타이틀은 페이지 내부에서 표시.
 */
export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <main className="flex min-h-screen items-center justify-center px-4 py-12">
      <div className="w-full max-w-sm">{children}</div>
    </main>
  );
}
