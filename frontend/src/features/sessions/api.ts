/**
 * 세션 도메인 API 호출.
 *
 * <p>설계: docs/design.md 3.5 /sessions 계열
 */
import { api } from "@/lib/api";
import type {
  SessionCreateRequest,
  SessionCreateResponse,
  SessionDetailResponse,
  SessionPageResponse,
} from "@/types/api";

export async function fetchSessions(page: number, size = 20): Promise<SessionPageResponse> {
  const { data } = await api.get<SessionPageResponse>("/sessions", {
    params: { page, size },
  });
  return data;
}

export async function fetchSession(id: number): Promise<SessionDetailResponse> {
  const { data } = await api.get<SessionDetailResponse>(`/sessions/${id}`);
  return data;
}

export async function createSession(payload: SessionCreateRequest): Promise<SessionCreateResponse> {
  const { data } = await api.post<SessionCreateResponse>("/sessions", payload);
  return data;
}

export async function deleteSession(id: number): Promise<void> {
  await api.delete(`/sessions/${id}`);
}
