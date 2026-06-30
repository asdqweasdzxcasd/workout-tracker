/**
 * 인증 흐름 E2E 테스트 (D.2 이메일 인증 포함).
 *
 * <p>검증 시나리오:
 * <ol>
 *   <li>회원가입 → 코드획득 → verify → login → /sessions (전체 해피패스).</li>
 *   <li>잘못된 코드 → 에러 메시지 노출(재시도 허용).</li>
 *   <li>미인증 상태 로그인 시도 → /verify-email 리다이렉트.</li>
 *   <li>재발송 → 60초 쿨다운 카운트다운 노출.</li>
 *   <li>잘못된 비밀번호 로그인 → 에러 메시지.</li>
 *   <li>비로그인 /sessions 접근 → /login 리다이렉트.</li>
 *   <li>비밀번호 클라이언트 검증 차단.</li>
 * </ol>
 *
 * <p>만료/시도초과는 시간/카운터 의존이라 E2E 에서 스킵 — 백엔드 단위테스트
 * (EmailVerificationServiceTest)가 커버한다.
 *
 * <p>각 테스트는 유니크 이메일을 사용해 서로 간섭하지 않는다. 인증 코드는 테스트 전용
 * 엔드포인트(!prod)로 획득한다(fetchVerificationCode).
 */
import { expect, test } from "@playwright/test";

import {
  DEFAULT_PASSWORD,
  fetchVerificationCode,
  login,
  signupAndLogin,
  uniqueEmail,
} from "./helpers/auth";

test.describe("인증 흐름", () => {
  test("회원가입 → 이메일 인증 → 로그인 후 세션 목록 페이지로 이동한다", async ({ page }) => {
    // Given - 새로운 계정 정보
    const email = uniqueEmail("signup");

    // When - 가입 → 코드획득 → verify → login (헬퍼가 전체 흐름 수행)
    const { email: createdEmail } = await signupAndLogin(page, { email });

    // Then - /sessions 로 이동했고 가드 통과
    expect(createdEmail).toBe(email);
    await expect(page).toHaveURL(/\/sessions(\?.*)?$/);
    await expect(page.getByRole("heading", { name: "내 운동 기록" })).toBeVisible();

    // localStorage 에 access_token 이 저장됐는지 확인
    const token = await page.evaluate(() => window.localStorage.getItem("access_token"));
    expect(token, "access_token 이 localStorage 에 저장되어야 한다").toBeTruthy();
  });

  test("잘못된 인증 코드를 입력하면 에러 메시지가 노출된다", async ({ page }) => {
    // Given - 가입만 한 미인증 상태로 /verify-email 도달
    const email = uniqueEmail("badcode");
    await page.goto("/signup");
    await page.getByLabel("이메일", { exact: false }).fill(email);
    await page.getByLabel("비밀번호", { exact: false }).fill(DEFAULT_PASSWORD);
    await page.getByLabel("닉네임", { exact: false }).fill("badcode");
    await page.getByRole("button", { name: "회원가입" }).click();
    await page.waitForURL("**/verify-email**");

    // When - 일부러 틀린 6자리 코드 제출
    await page.getByLabel("인증 코드", { exact: false }).fill("000000");
    const verifyResp = page.waitForResponse(
      (resp) => resp.url().includes("/auth/verify-email") && resp.request().method() === "POST",
    );
    await page.getByRole("button", { name: "인증 완료" }).click();
    const resp = await verifyResp;

    // Then - 백엔드 4xx + 에러 메시지 노출, URL 은 verify-email 유지
    expect(resp.status()).toBeGreaterThanOrEqual(400);
    expect(resp.status()).toBeLessThan(500);
    await expect(page.getByText(/인증 코드가|만료/)).toBeVisible();
    await expect(page).toHaveURL(/\/verify-email/);
  });

  test("미인증 계정으로 로그인하면 /verify-email 로 리다이렉트된다", async ({ page }) => {
    // Given - 가입만 하고 인증은 건너뛴 계정
    const email = uniqueEmail("unverified");
    await page.goto("/signup");
    await page.getByLabel("이메일", { exact: false }).fill(email);
    await page.getByLabel("비밀번호", { exact: false }).fill(DEFAULT_PASSWORD);
    await page.getByLabel("닉네임", { exact: false }).fill("unverif");
    await page.getByRole("button", { name: "회원가입" }).click();
    await page.waitForURL("**/verify-email**");

    // When - 인증하지 않은 채 /login 에서 로그인 시도
    await page.goto("/login");
    await page.getByLabel("이메일", { exact: false }).fill(email);
    await page.getByLabel("비밀번호", { exact: false }).fill(DEFAULT_PASSWORD);
    const loginResp = page.waitForResponse(
      (resp) => resp.url().includes("/auth/login") && resp.request().method() === "POST",
    );
    await page.getByRole("button", { name: "로그인", exact: true }).click();
    await loginResp;

    // Then - EMAIL_NOT_VERIFIED(403) → /verify-email 로 이동(from=login 안내 배너)
    await expect(page).toHaveURL(/\/verify-email/);
    await expect(page.getByText("로그인하려면 먼저 이메일 인증이 필요합니다")).toBeVisible();
  });

  test("인증 코드를 재발송하면 60초 쿨다운 카운트다운이 노출된다", async ({ page }) => {
    // Given - 미인증 상태로 /verify-email 도달
    const email = uniqueEmail("resend");
    await page.goto("/signup");
    await page.getByLabel("이메일", { exact: false }).fill(email);
    await page.getByLabel("비밀번호", { exact: false }).fill(DEFAULT_PASSWORD);
    await page.getByLabel("닉네임", { exact: false }).fill("resend");
    await page.getByRole("button", { name: "회원가입" }).click();
    await page.waitForURL("**/verify-email**");

    // When - 재발송 버튼 클릭 (202 → 쿨다운 시작)
    const resendResp = page.waitForResponse(
      (resp) =>
        resp.url().includes("/auth/verify-email/resend") && resp.request().method() === "POST",
    );
    await page.getByRole("button", { name: /재발송|인증 코드 재발송/ }).click();
    await resendResp;

    // Then - 버튼이 "초 후 가능" 카운트다운 + 비활성, 안내 메시지 노출
    await expect(page.getByRole("button", { name: /초 후 가능/ })).toBeVisible();
    await expect(page.getByRole("button", { name: /초 후 가능/ })).toBeDisabled();
    await expect(page.getByText(/다시 보냈습니다/)).toBeVisible();

    // 재발송 후 새 코드로도 정상 인증되는지 sanity (흐름 연결 확인)
    const code = await fetchVerificationCode(page, email);
    await page.getByLabel("인증 코드", { exact: false }).fill(code);
    await page.getByRole("button", { name: "인증 완료" }).click();
    await page.waitForURL("**/login**");
    await expect(page.getByText("이메일 인증이 완료되었습니다")).toBeVisible();
  });

  test("잘못된 비밀번호로 로그인하면 에러 메시지가 노출된다", async ({ page }) => {
    // Given - 정상 가입+인증된 계정
    const credentials = await signupAndLogin(page);
    await page.evaluate(() => window.localStorage.removeItem("access_token"));

    // When - 비밀번호를 틀려서 로그인 시도
    await page.goto("/login");
    await page.getByLabel("이메일", { exact: false }).fill(credentials.email);
    await page.getByLabel("비밀번호", { exact: false }).fill("WrongPass1!");
    const loginResp = page.waitForResponse(
      (resp) => resp.url().includes("/auth/login") && resp.request().method() === "POST",
    );
    await page.getByRole("button", { name: "로그인", exact: true }).click();
    const resp = await loginResp;

    // Then - 4xx + 에러 메시지, URL 은 /login 유지, 토큰 미저장
    expect(resp.status()).toBeGreaterThanOrEqual(400);
    expect(resp.status()).toBeLessThan(500);
    await expect(page.getByText("이메일 또는 비밀번호가 올바르지 않습니다")).toBeVisible();
    await expect(page).toHaveURL(/\/login(\?.*)?$/);
    const token = await page.evaluate(() => window.localStorage.getItem("access_token"));
    expect(token).toBeNull();
  });

  test("정상 자격증명으로 로그인하면 /sessions 로 리다이렉트된다", async ({ page }) => {
    // Given - 가입+인증된 계정 (이후 토큰만 지워 비로그인 상태로)
    const credentials = await signupAndLogin(page);
    await page.evaluate(() => window.localStorage.removeItem("access_token"));

    // When - 명시적으로 다시 로그인
    await login(page, credentials.email, credentials.password);

    // Then - 세션 목록 페이지 + 토큰 저장
    await expect(page).toHaveURL(/\/sessions(\?.*)?$/);
    await expect(page.getByRole("heading", { name: "내 운동 기록" })).toBeVisible();
    const token = await page.evaluate(() => window.localStorage.getItem("access_token"));
    expect(token).toBeTruthy();
  });

  test("비로그인 상태에서 /sessions 접근하면 /login 으로 리다이렉트된다", async ({ page }) => {
    // Given - 깨끗한 컨텍스트
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
    await expect(page.getByLabel("비밀번호", { exact: false })).toHaveAttribute("aria-invalid", "true");
  });
});
