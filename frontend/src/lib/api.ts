/**
 * axios 인스턴스 + 인터셉터 (Access + Refresh 토큰 회전 지원).
 *
 * <p>설계: docs/design.md 4.4 인증 가드, 5.2 컴포넌트 간 통신, 부록 D.1
 *
 * <p>흐름:
 * <ol>
 *   <li>모든 호출은 baseURL <code>/api/proxy</code> (BFF) 로 향한다.</li>
 *   <li>요청 인터셉터: localStorage 의 access_token 을 Authorization 헤더로 자동 첨부.</li>
 *   <li>응답 인터셉터: 401 발생 시 <code>/auth/refresh</code> 로 자동 재발급 후 원 요청 재시도.
 *     <ul>
 *       <li>동시 401 다발 시 refresh 요청은 <b>1번만</b> 보내고, 다른 요청들은 대기 큐에서 결과를 받음.</li>
 *       <li>refresh 자체가 실패 / REUSED / EXPIRED → 즉시 로그아웃 + /login 리다이렉트.</li>
 *     </ul>
 *   </li>
 * </ol>
 */
import axios, { AxiosError, type InternalAxiosRequestConfig } from "axios";

import type { ApiErrorResponse, LoginResponse } from "@/types/api";
import {
  clearTokens,
  getAccessToken,
  getRefreshToken,
  setTokens,
} from "./auth-storage";

const baseURL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "/api/proxy";

export const api = axios.create({
  baseURL,
  timeout: 10_000,
  headers: { "Content-Type": "application/json" },
});

// --- 요청 인터셉터: Bearer 토큰 첨부 ---
api.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    config.headers.set("Authorization", `Bearer ${token}`);
  }
  return config;
});

// --- 응답 인터셉터: 401 → /auth/refresh 자동 재시도 ---

interface RetriableConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

/** refresh 진행 중인 동안 다른 401 요청들이 대기하는 큐. */
let isRefreshing = false;
let waitQueue: Array<(newAccessToken: string | null) => void> = [];

function flushQueue(newAccessToken: string | null): void {
  for (const resolver of waitQueue) resolver(newAccessToken);
  waitQueue = [];
}

function redirectToLogin(): void {
  clearTokens();
  if (typeof window === "undefined") return;
  if (!window.location.pathname.startsWith("/login")) {
    const next = encodeURIComponent(window.location.pathname);
    window.location.href = `/login?next=${next}`;
  }
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiErrorResponse>) => {
    const original = error.config as RetriableConfig | undefined;
    const status = error.response?.status;
    const code = error.response?.data?.code;

    // 401 이 아니거나 config 가 없으면 그대로 reject
    if (status !== 401 || !original) {
      return Promise.reject(error);
    }

    // /auth/refresh 자체가 실패하면 즉시 로그아웃
    if (typeof original.url === "string" && original.url.includes("/auth/refresh")) {
      redirectToLogin();
      return Promise.reject(error);
    }

    // 이미 한 번 재시도한 요청은 더 안 시도 (무한 루프 방지)
    if (original._retry) {
      redirectToLogin();
      return Promise.reject(error);
    }
    original._retry = true;

    // 백엔드가 명시적으로 REUSED / EXPIRED 신호 보낸 경우 refresh 시도 의미 없음
    if (code === "REFRESH_TOKEN_REUSED" || code === "REFRESH_TOKEN_EXPIRED") {
      redirectToLogin();
      return Promise.reject(error);
    }

    // 이미 진행 중인 refresh 가 있으면 큐에 대기
    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        waitQueue.push((newAccessToken) => {
          if (!newAccessToken) {
            reject(error);
            return;
          }
          original.headers.set("Authorization", `Bearer ${newAccessToken}`);
          resolve(api(original));
        });
      });
    }

    // 내가 refresh 주체
    isRefreshing = true;
    const refreshToken = getRefreshToken();
    if (!refreshToken) {
      isRefreshing = false;
      flushQueue(null);
      redirectToLogin();
      return Promise.reject(error);
    }

    try {
      // axios.post 를 별도 인스턴스 호출로 — 인터셉터 재귀 방지
      const { data } = await axios.post<LoginResponse>(
        `${baseURL}/auth/refresh`,
        { refreshToken },
        { headers: { "Content-Type": "application/json" } },
      );
      setTokens(data.accessToken, data.refreshToken);
      flushQueue(data.accessToken);
      original.headers.set("Authorization", `Bearer ${data.accessToken}`);
      return api(original);
    } catch (refreshError) {
      flushQueue(null);
      redirectToLogin();
      return Promise.reject(refreshError);
    } finally {
      isRefreshing = false;
    }
  },
);

/**
 * 백엔드 에러 응답에서 사용자에게 보여줄 메시지 추출.
 *
 * <p>우선순위:
 * <ol>
 *   <li>ErrorResponse.message</li>
 *   <li>axios 기본 message</li>
 *   <li>fallback 문자열</li>
 * </ol>
 */
export function extractErrorMessage(error: unknown, fallback = "요청 처리 중 오류가 발생했습니다."): string {
  if (axios.isAxiosError<ApiErrorResponse>(error)) {
    return error.response?.data?.message ?? error.message ?? fallback;
  }
  if (error instanceof Error) return error.message;
  return fallback;
}

/**
 * 백엔드 에러 응답의 비즈니스 코드(ErrorResponse.code) 추출.
 *
 * <p>메시지는 사용자에게 그대로 노출하되, <b>상태 전환</b>(재발송 강조/입력 비활성 등)은
 * 이 code 로만 분기한다. axios 에러가 아니거나 code 가 없으면 null.</p>
 */
export function extractErrorCode(error: unknown): string | null {
  if (axios.isAxiosError<ApiErrorResponse>(error)) {
    return error.response?.data?.code ?? null;
  }
  return null;
}

/** 백엔드 에러 응답의 HTTP 상태 코드 추출 (없으면 null). */
export function extractErrorStatus(error: unknown): number | null {
  if (axios.isAxiosError<ApiErrorResponse>(error)) {
    return error.response?.status ?? null;
  }
  return null;
}

/**
 * 429 응답의 {@code Retry-After} 헤더(초)를 파싱. 헤더가 없거나 파싱 불가하면 null.
 *
 * <p>백엔드가 Retry-After 를 주지 않을 가능성이 높아, 호출부는 null 일 때 기본 60초로 대체한다.</p>
 */
export function extractRetryAfterSeconds(error: unknown): number | null {
  if (!axios.isAxiosError(error)) return null;
  const raw = error.response?.headers?.["retry-after"];
  if (raw == null) return null;
  const seconds = Number(raw);
  return Number.isFinite(seconds) && seconds > 0 ? Math.ceil(seconds) : null;
}
