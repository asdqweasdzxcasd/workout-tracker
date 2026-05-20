/**
 * 인증 관련 E2E 헬퍼.
 *
 * <p>각 테스트는 자기 데이터를 만들고 시작해야 한다(독립성). 매 호출마다 유니크 이메일 생성.
 *
 * <p>경로:
 * <ul>
 *   <li>회원가입 페이지 (/signup) 에서 폼 제출 → 성공 시 백엔드 응답 후 자동 로그인 →
 *       /sessions 로 리다이렉트.</li>
 *   <li>localStorage 의 access_token 까지 세팅되어야 인증 가드 통과.</li>
 * </ul>
 */
import type { Page } from "@playwright/test";

/** 매번 충돌 없이 새 계정을 만들기 위한 이메일 생성기. */
export function uniqueEmail(prefix = "e2e"): string {
  // Date.now() 만으로는 동일 ms 충돌 가능 - 랜덤 suffix 추가
  const rand = Math.random().toString(36).slice(2, 8);
  return `${prefix}-${Date.now()}-${rand}@test.local`;
}

/** 기본 비밀번호 (signupSchema: 영문+숫자 8자 이상). */
export const DEFAULT_PASSWORD = "Passw0rd!";

export interface SignupCredentials {
  email: string;
  password: string;
  nickname: string;
}

/**
 * 새 계정 생성 + 자동 로그인 후 /sessions 도달까지 보장.
 *
 * <p>signup 페이지의 흐름이 "signup → 즉시 login → /sessions" 이므로
 * 폼 제출 후 URL 이 /sessions 로 바뀌고 access_token 이 localStorage 에 들어간 것까지 검증.
 */
export async function signupAndLogin(
  page: Page,
  overrides: Partial<SignupCredentials> = {},
): Promise<SignupCredentials> {
  const credentials: SignupCredentials = {
    email: overrides.email ?? uniqueEmail(),
    password: overrides.password ?? DEFAULT_PASSWORD,
    nickname: overrides.nickname ?? `e2e${Math.floor(Math.random() * 10000)}`,
  };

  await page.goto("/signup");

  await page.getByLabel("이메일", { exact: false }).fill(credentials.email);
  await page.getByLabel("비밀번호", { exact: false }).fill(credentials.password);
  await page.getByLabel("닉네임", { exact: false }).fill(credentials.nickname);

  // 회원가입 → 자동 로그인 → /sessions 리다이렉트 까지 한 번에 기다린다.
  // /auth/login 응답을 기다리면 토큰 저장이 끝난 시점을 보장할 수 있다.
  // BCrypt + 자동 로그인이 직렬화되므로 frontend axios timeout(10s) 보다는 짧게 잡되,
  // 로컬 콜드스타트 여유로 25초까지 허용.
  const loginResponse = page.waitForResponse(
    (resp) => resp.url().includes("/auth/login") && resp.request().method() === "POST",
    { timeout: 25_000 },
  );
  await page.getByRole("button", { name: "회원가입" }).click();
  await loginResponse;

  await page.waitForURL("**/sessions", { timeout: 10_000 });
  return credentials;
}

/**
 * 이미 가입된 계정으로 로그인. (회원가입 충돌 회피용)
 */
export async function login(page: Page, email: string, password: string): Promise<void> {
  await page.goto("/login");
  await page.getByLabel("이메일", { exact: false }).fill(email);
  await page.getByLabel("비밀번호", { exact: false }).fill(password);

  const loginResponse = page.waitForResponse(
    (resp) => resp.url().includes("/auth/login") && resp.request().method() === "POST",
  );
  await page.getByRole("button", { name: "로그인", exact: true }).click();
  await loginResponse;
  await page.waitForURL("**/sessions");
}
