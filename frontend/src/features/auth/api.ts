/**
 * 인증 도메인 API 호출 모듈.
 *
 * <p>설계: docs/design.md 3.5, 부록 D.1
 *
 * <p>엔드포인트:
 * <ul>
 *   <li>POST /auth/signup — 회원가입</li>
 *   <li>POST /auth/login — 로그인 (Access + Refresh 발급)</li>
 *   <li>POST /auth/refresh — 토큰 회전 (lib/api.ts 의 인터셉터가 자동 호출)</li>
 *   <li>POST /auth/logout — 현재 기기 세션 무효화 (refreshToken 제공) 또는 전체 세션</li>
 *   <li>POST /auth/logout-all — 전체 기기 세션 무효화</li>
 *   <li>GET /auth/me — 현재 사용자 정보</li>
 * </ul>
 */
import { api } from "@/lib/api";
import { clearTokens, getRefreshToken } from "@/lib/auth-storage";
import type {
  LoginRequest,
  LoginResponse,
  MeResponse,
  ResendVerificationRequest,
  SignupRequest,
  SignupResponse,
  VerifyEmailRequest,
} from "@/types/api";

export async function signup(payload: SignupRequest): Promise<SignupResponse> {
  const { data } = await api.post<SignupResponse>("/auth/signup", payload);
  return data;
}

export async function login(payload: LoginRequest): Promise<LoginResponse> {
  const { data } = await api.post<LoginResponse>("/auth/login", payload);
  return data;
}

/**
 * 이메일 인증 코드 검증.
 *
 * <p>성공 시 본문 없는 200. 실패는 백엔드 ErrorResponse(code/message)로 전달되며
 * 호출부가 code 로 상태 전환(재발송 강조/비활성)을 분기한다.</p>
 */
export async function verifyEmail(payload: VerifyEmailRequest): Promise<void> {
  await api.post("/auth/verify-email", payload);
}

/**
 * 이메일 인증 코드 재발송.
 *
 * <p>이메일 열거 방어로 항상 202(본문 없음). 레이트리밋 초과 시에만 429 가 발생한다.</p>
 */
export async function resendVerification(payload: ResendVerificationRequest): Promise<void> {
  await api.post("/auth/verify-email/resend", payload);
}

export async function fetchMe(): Promise<MeResponse> {
  const { data } = await api.get<MeResponse>("/auth/me");
  return data;
}

// ==================== OAuth 소셜 로그인 (D.3) ====================

export type OAuthProvider = "google" | "naver" | "kakao";

/**
 * 소셜 로그인 시작 URL.
 *
 * <p>authorize 는 전체 페이지 리다이렉트 흐름이라 BFF 프록시를 타지 않고
 * 브라우저가 백엔드로 직접 이동한다 (provider 콘솔의 redirect_uri 도 백엔드 도메인).
 * 성공 시 백엔드가 프론트 /oauth/callback?code= 로 되돌려준다.
 */
export function oauthAuthorizeUrl(provider: OAuthProvider): string {
  const base = process.env.NEXT_PUBLIC_OAUTH_BASE_URL ?? "http://localhost:8080";
  return `${base}/oauth2/authorization/${provider}`;
}

/**
 * 소셜 로그인 1회용 code → 자체 Access/Refresh 토큰 교환.
 *
 * <p>code 는 60초 유효·1회용. 실패(만료/재사용)는 401 ErrorResponse 로 온다.</p>
 */
export async function exchangeOAuthCode(code: string): Promise<LoginResponse> {
  const { data } = await api.post<LoginResponse>("/auth/oauth/exchange", { code });
  return data;
}

/**
 * 로그아웃 — 현재 기기 세션만 무효화.
 *
 * <p>refreshToken 이 있으면 서버측 Redis 에서 해당 jti 만 삭제 (다른 기기 세션은 살아있음).
 * 서버 응답에 무관하게 클라이언트 토큰은 정리한다 (네트워크 실패해도 UX 진행).</p>
 */
export async function logout(): Promise<void> {
  const refreshToken = getRefreshToken();
  try {
    await api.post("/auth/logout", refreshToken ? { refreshToken } : undefined);
  } catch {
    // 서버 호출 실패해도 로컬은 정리 — 사용자 경험 우선
  } finally {
    clearTokens();
  }
}

/** 모든 기기 세션 무효화 (다중 기기 강제 로그아웃). */
export async function logoutAll(): Promise<void> {
  try {
    await api.post("/auth/logout-all");
  } catch {
    // 동일
  } finally {
    clearTokens();
  }
}
