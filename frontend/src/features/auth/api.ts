/**
 * 인증 도메인 API 호출 모듈.
 *
 * <p>설계: docs/design.md 3.5 POST /auth/signup, /auth/login, GET /auth/me
 */
import { api } from "@/lib/api";
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
