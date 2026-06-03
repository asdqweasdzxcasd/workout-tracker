/**
 * 토큰 저장소 (localStorage 래퍼) — Access + Refresh.
 *
 * <p>MVP 는 Bearer + localStorage 방식 (docs/design.md 4.4 참조).
 * 운영 마이그레이션 시 HttpOnly Secure 쿠키로 전환 예정 (부록 C).
 *
 * <p>설계:
 * <ul>
 *   <li>Access Token: 짧은 수명 (15분). 매 요청 Authorization 헤더로 사용.</li>
 *   <li>Refresh Token: 긴 수명 (14일). 401 시 /auth/refresh 호출에 사용.
 *       서버측 Redis 에서도 관리 — 로그아웃 시 즉시 무효화 가능.</li>
 * </ul>
 *
 * <p>SSR 안전: typeof window 가드.
 */
const ACCESS_TOKEN_KEY = "access_token";
const REFRESH_TOKEN_KEY = "refresh_token";

export function getAccessToken(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function setAccessToken(token: string): void {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(ACCESS_TOKEN_KEY, token);
}

export function clearAccessToken(): void {
  if (typeof window === "undefined") return;
  window.localStorage.removeItem(ACCESS_TOKEN_KEY);
}

export function getRefreshToken(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function setRefreshToken(token: string): void {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(REFRESH_TOKEN_KEY, token);
}

export function clearRefreshToken(): void {
  if (typeof window === "undefined") return;
  window.localStorage.removeItem(REFRESH_TOKEN_KEY);
}

/** 로그인/refresh 성공 시 한 번에 저장. */
export function setTokens(accessToken: string, refreshToken: string): void {
  setAccessToken(accessToken);
  setRefreshToken(refreshToken);
}

/** 로그아웃/401 시 둘 다 정리. */
export function clearTokens(): void {
  clearAccessToken();
  clearRefreshToken();
}
