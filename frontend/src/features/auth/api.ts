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
  SignupRequest,
  SignupResponse,
} from "@/types/api";

export async function signup(payload: SignupRequest): Promise<SignupResponse> {
  const { data } = await api.post<SignupResponse>("/auth/signup", payload);
  return data;
}

export async function login(payload: LoginRequest): Promise<LoginResponse> {
  const { data } = await api.post<LoginResponse>("/auth/login", payload);
  return data;
}

export async function fetchMe(): Promise<MeResponse> {
  const { data } = await api.get<MeResponse>("/auth/me");
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
