/**
 * React Query 쿼리 키 규약.
 *
 * <p>설계: docs/design.md 4.3 React Query 사용 패턴 (Query Key 규약)
 *
 * <p>모든 키는 함수 또는 const 로 노출해서 호출부에서 오타가 나면 컴파일 에러가 나게 한다.
 * invalidateQueries 도 본 모듈의 키를 그대로 사용해서 stale 처리 누락을 막는다.
 */
export const qk = {
  me: ["me"] as const,
  exercises: (bodyPart?: string) => ["exercises", { bodyPart: bodyPart ?? null }] as const,
  sessions: (page: number) => ["sessions", { page }] as const,
  sessionsAll: ["sessions"] as const, // invalidateQueries 용 prefix
  session: (id: number) => ["sessions", id] as const,
};
