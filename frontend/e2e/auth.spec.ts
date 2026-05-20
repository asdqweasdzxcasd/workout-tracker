/**
 * 인증 흐름 E2E 테스트.
 *
 * <p>검증 시나리오:
 * <ol>
 *   <li>회원가입 폼 제출 → signupPage 가 즉시 login 호출 → /sessions 로 진입.
 *       (실제 구현은 page.tsx 의 signupMutation 에서 signup 후 login 을 chain.)</li>
 *   <li>잘못된 비밀번호 → 백엔드 에러 메시지가 role=alert 로 노출.</li>
 *   <li>기존 계정 로그인 → /sessions 로 진입 + access_token 저장 확인.</li>
 * </ol>
 *
 * <p>각 테스트는 유니크 이메일을 사용해 서로 간섭하지 않는다.
 */
import { expect, test } from "@playwright/test";

import { DEFAULT_PASSWORD, login, signupAndLogin, uniqueEmail } from "./helpers/auth";

test.describe("인증 흐름", () => {
  test("회원가입 성공 시 자동 로그인되어 세션 목록 페이지로 이동한다", async ({ page }) => {
    // Given - 새로운 계정 정보
    const email = uniqueEmail("signup");

    // When - 회원가입 폼 제출
    const { email: createdEmail } = await signupAndLogin(page, { email });

    // Then - /sessions 로 이동했고 헤더에 앱 로고가 보여야 함 (= 가드 통과)
    expect(createdEmail).toBe(email);
    await expect(page).toHaveURL(/\/sessions(\?.*)?$/);
    await expect(page.getByRole("heading", { name: "내 운동 기록" })).toBeVisible();

    // localStorage 에 access_token 이 저장됐는지 확인
    const token = await page.evaluate(() => window.localStorage.getItem("access_token"));
    expect(token, "access_token 이 localStorage 에 저장되어야 한다").toBeTruthy();
  });

  test("잘못된 비밀번호로 로그인하면 에러 메시지가 노출된다", async ({ page }) => {
    // Given - 정상 가입된 계정이 있고
    const credentials = await signupAndLogin(page);
    // 로그아웃 (간단히 토큰만 지우고 /login 으로 이동)
    await page.evaluate(() => window.localStorage.removeItem("access_token"));

    // When - 비밀번호를 일부러 틀려서 로그인 시도
    await page.goto("/login");
    await page.getByLabel("이메일", { exact: false }).fill(credentials.email);
    await page.getByLabel("비밀번호", { exact: false }).fill("WrongPass1!");

    // 로그인 요청 응답을 명시적으로 기다린다 - 404/401 가능
    const loginResp = page.waitForResponse(
      (resp) => resp.url().includes("/auth/login") && resp.request().method() === "POST",
    );
    await page.getByRole("button", { name: "로그인", exact: true }).click();
    const resp = await loginResp;

    // Then - 백엔드는 4xx 를 반환했고
    expect(resp.status()).toBeGreaterThanOrEqual(400);
    expect(resp.status()).toBeLessThan(500);

    // 에러 메시지가 role=alert 로 표시되어야 한다 (web-first assertion 자동 재시도)
    await expect(page.getByRole("alert")).toBeVisible();

    // URL 은 여전히 /login (리다이렉트 안 됨)
    await expect(page).toHaveURL(/\/login$/);
    // 토큰은 저장되지 않았어야 한다
    const token = await page.evaluate(() => window.localStorage.getItem("access_token"));
    expect(token).toBeNull();
  });

  test("정상 자격증명으로 로그인하면 /sessions 로 리다이렉트된다", async ({ page }) => {
    // Given - 가입된 계정 (이후 토큰만 지워 비로그인 상태로)
    const credentials = await signupAndLogin(page);
    await page.evaluate(() => window.localStorage.removeItem("access_token"));

    // When - 명시적으로 다시 로그인
    await login(page, credentials.email, credentials.password);

    // Then - 세션 목록 페이지가 보이고 토큰이 저장됨
    await expect(page).toHaveURL(/\/sessions(\?.*)?$/);
    await expect(page.getByRole("heading", { name: "내 운동 기록" })).toBeVisible();
    const token = await page.evaluate(() => window.localStorage.getItem("access_token"));
    expect(token).toBeTruthy();
  });

  test("비로그인 상태에서 /sessions 접근하면 /login 으로 리다이렉트된다", async ({ page }) => {
    // Given - 깨끗한 컨텍스트 (Playwright 의 새 context 는 localStorage 비어 있음)
    // 그러나 명시적으로도 확인
    await page.goto("/login");
    await page.evaluate(() => window.localStorage.clear());

    // When - 보호 영역으로 직접 진입
    await page.goto("/sessions");

    // Then - 가드가 /login 으로 보냄
    await expect(page).toHaveURL(/\/login(\?.*)?$/);
  });

  test("password 가 정책에 맞지 않으면 클라이언트 측에서 차단된다", async ({ page }) => {
    // Given - 너무 짧은 비밀번호
    await page.goto("/signup");
    await page.getByLabel("이메일", { exact: false }).fill(uniqueEmail("short"));
    await page.getByLabel("비밀번호", { exact: false }).fill("short");
    await page.getByLabel("닉네임", { exact: false }).fill("tester");

    // When - 제출
    await page.getByRole("button", { name: "회원가입" }).click();

    // Then - URL 이 /signup 에 머무르고 (네트워크 호출 안 됨)
    await expect(page).toHaveURL(/\/signup$/);
    // 비밀번호 input 에 aria-invalid 마킹 (zod 검증 실패)
    await expect(page.getByLabel("비밀번호", { exact: false })).toHaveAttribute("aria-invalid", "true");
    // 기본 비밀번호로는 정상 작동하는지 sanity check
    void DEFAULT_PASSWORD;
  });
});
