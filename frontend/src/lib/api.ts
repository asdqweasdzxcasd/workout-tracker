/**
 * axios 인스턴스 + 인터셉터.
 *
 * <p>설계: docs/design.md 4.4 인증 가드, 5.2 컴포넌트 간 통신
 *
 * <p>흐름:
 * <ol>
 *   <li>모든 호출은 baseURL <code>/api/proxy</code> (BFF) 로 향한다.</li>
 *   <li>요청 인터셉터: localStorage 의 access_token 을 Authorization 헤더로 자동 첨부.</li>
 *   <li>응답 인터셉터: 401 발생 시 토큰 제거 + /login 으로 리다이렉트.</li>
 * </ol>
 */
import axios, { AxiosError } from "axios";

import type { ApiErrorResponse } from "@/types/api";
import { clearAccessToken, getAccessToken } from "./auth-storage";

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

// --- 응답 인터셉터: 401 자동 로그아웃 ---
api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiErrorResponse>) => {
    if (error.response?.status === 401 && typeof window !== "undefined") {
      clearAccessToken();
      // 무한 루프 방지: 이미 /login 페이지면 그대로 둔다.
      if (!window.location.pathname.startsWith("/login")) {
        const next = encodeURIComponent(window.location.pathname);
        window.location.href = `/login?next=${next}`;
      }
    }
    return Promise.reject(error);
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
