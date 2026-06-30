/**
 * 인증 관련 E2E 헬퍼.
 *
 * <p>각 테스트는 자기 데이터를 만들고 시작해야 한다(독립성). 매 호출마다 유니크 이메일 생성.
 *
 * <p>D.2 이메일 인증 도입 후 흐름:
 * <ul>
 *   <li>회원가입(/signup) → /verify-email 로 이동(자동 로그인 X).</li>
 *   <li>테스트 전용 엔드포인트로 발급 코드를 받아 입력 → 인증 완료 → /login?verified=1.</li>
 *   <li>로그인(/login) 에서 비번 재입력 → /sessions 도달 + access_token 저장.</li>
 * </ul>
 *
 * <p>코드 획득: 운영에는 노출되지 않는({@code !prod}) 테스트 엔드포인트를 BFF 프록시 경유로 호출한다
 * (<code>GET /api/proxy/test/last-verification-code?email=</code>).
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
 * 테스트 전용 엔드포인트에서 마지막 발급 인증 코드를 가져온다.
 *
 * <p>BFF 프록시(<code>/api/proxy/test/last-verification-code</code>)를 page.request 로 호출한다.
 * 백엔드는 {@code !prod} 프로필에서만 이 경로를 공개하므로 로컬/CI E2E 에서만 동작한다.
 *
 * @throws 코드가 아직 적재되지 않았으면(발송 비동기 지연) 짧게 재시도 후 실패.
 */
export async function fetchVerificationCode(page: Page, email: string): Promise<string> {
  const url = `/api/proxy/test/last-verification-code?email=${encodeURIComponent(email)}`;

  // 가입 후 인증 메일 발송은 AFTER_COMMIT + @Async 라 코드 적재가 약간 지연될 수 있다.
  // 짧은 폴링으로 최대 ~5초 대기.
  const maxAttempts = 10;
  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    const resp = await page.request.get(url);
    if (resp.ok()) {
      const code = (await resp.text()).trim();
      if (/^\d{6}$/.test(code)) return code;
    }
    await page.waitForTimeout(500);
  }
  throw new Error(`인증 코드를 가져오지 못했습니다: email=${email}`);
}

/**
 * 새 계정 생성 + 이메일 인증 + 로그인 후 /sessions 도달까지 보장.
 *
 * <p>이름은 기존 호출처 호환을 위해 유지한다. 내부 흐름만 D.2 에 맞춰 교체:
 * 가입 → 코드획득 → verify → login → /sessions.
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

  // 1) 회원가입 → /verify-email 로 이동.
  await page.goto("/signup");
  await page.getByLabel("이메일", { exact: false }).fill(credentials.email);
  await page.getByLabel("비밀번호", { exact: false }).fill(credentials.password);
  await page.getByLabel("닉네임", { exact: false }).fill(credentials.nickname);
  await page.getByRole("button", { name: "회원가입" }).click();
  await page.waitForURL("**/verify-email**", { timeout: 15_000 });

  // 2) 발급 코드 획득 → 입력 → 인증 완료 → /login?verified=1.
  const code = await fetchVerificationCode(page, credentials.email);
  await page.getByLabel("인증 코드", { exact: false }).fill(code);

  const verifyResponse = page.waitForResponse(
    (resp) => resp.url().includes("/auth/verify-email") && resp.request().method() === "POST",
    { timeout: 15_000 },
  );
  await page.getByRole("button", { name: "인증 완료" }).click();
  await verifyResponse;
  await page.waitForURL("**/login**", { timeout: 10_000 });

  // 3) 로그인(비번 재입력) → /sessions.
  await login(page, credentials.email, credentials.password);
  return credentials;
}

/**
 * 이미 가입+인증된 계정으로 로그인. (verify 이후 단계 / 회원가입 충돌 회피용)
 *
 * <p>signupAndLogin 이 verify 후 이미 /login?verified=1&email= 로 이동시키므로,
 * 명시적으로 /login 으로 다시 이동해 깨끗한 상태에서 입력한다.
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
  await page.waitForURL("**/sessions", { timeout: 10_000 });
}
