/**
 * 액세스 토큰 저장소 (localStorage 래퍼).
 *
 * <p>MVP 는 Bearer + localStorage 방식 (docs/design.md 4.4 참조).
 * 운영 마이그레이션 시 HttpOnly Secure 쿠키로 전환 예정.
 *
 * <p>SSR 안전: typeof window 가드.
 */
const ACCESS_TOKEN_KEY = "access_token";

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
