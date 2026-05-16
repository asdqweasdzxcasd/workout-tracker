/**
 * 랜딩 페이지 (Day 1 스캐폴딩).
 *
 * 실제 라우팅(/login → /sessions 리다이렉트 등)은 Day 4 작업.
 * 지금은 빌드/실행 확인용 자리표시자.
 */
export default function Home() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center bg-zinc-50 p-8 dark:bg-black">
      <h1 className="text-3xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
        workout-tracker
      </h1>
    </main>
  );
}
