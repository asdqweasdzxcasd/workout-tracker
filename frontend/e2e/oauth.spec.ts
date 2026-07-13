/**
 * OAuth 소셜 로그인 E2E (D.3) — 실제 provider 없이 검증 가능한 구간.
 *
 * <p>검증 시나리오:
 * <ol>
 *   <li>로그인 화면에 소셜 버튼 3종이 백엔드 authorize 진입점을 가리킨다.</li>
 *   <li>버튼 클릭 → 백엔드가 provider authorization URL 로 302 (state 포함).</li>
 *   <li>/oauth/callback 에 code 없이 접근 → /login?error=oauth + 실패 배너.</li>
 *   <li>무효 code 로 접근 → exchange 401 → /login?error=oauth.</li>
 * </ol>
 *
 * <p>실제 3사 로그인(동의 화면)은 외부 계정 의존이라 E2E 제외 — 배포 후 수동
 * 스모크(tasks 8.2)로 검증한다. state/exchange 의 서버 동작은 백엔드
 * OAuthFlowIntegrationTest 가 커버한다.
 */
import { expect, test } from "@playwright/test";

const PROVIDERS = [
  { id: "google", authHost: "accounts.google.com" },
  { id: "naver", authHost: "nid.naver.com" },
  { id: "kakao", authHost: "kauth.kakao.com" },
] as const;

test.describe("OAuth 소셜 로그인", () => {
  test("로그인 화면에 소셜 버튼 3종이 노출된다", async ({ page }) => {
    await page.goto("/login");

    for (const { id } of PROVIDERS) {
      const button = page.getByTestId(`oauth-${id}`);
      await expect(button).toBeVisible();
      await expect(button).toHaveAttribute("href", new RegExp(`/oauth2/authorization/${id}$`));
    }
  });

  for (const { id, authHost } of PROVIDERS) {
    test(`${id} authorize 진입점이 provider(${authHost})로 302 리다이렉트한다`, async ({
      page,
      request,
    }) => {
      await page.goto("/login");
      const href = await page.getByTestId(`oauth-${id}`).getAttribute("href");
      expect(href).toBeTruthy();

      // 리다이렉트를 따라가지 않고 Location 만 검사 (실 provider 페이지 로딩 회피)
      const response = await request.get(href as string, { maxRedirects: 0 });
      expect(response.status()).toBeGreaterThanOrEqual(300);
      expect(response.status()).toBeLessThan(400);
      const location = response.headers()["location"] ?? "";
      expect(location).toContain(authHost);
      expect(location).toContain("state=");
    });
  }

  test("콜백에 code 가 없으면 로그인 화면으로 돌아가 실패 배너를 보여준다", async ({ page }) => {
    await page.goto("/oauth/callback");

    await page.waitForURL(/\/login\?error=oauth/);
    await expect(page.getByRole("alert")).toContainText("소셜 로그인에 실패했습니다");
  });

  test("무효한 exchange code 는 로그인 화면으로 돌아간다 (1회용/만료)", async ({ page }) => {
    await page.goto("/oauth/callback?code=invalid-or-expired-code");

    await page.waitForURL(/\/login\?error=oauth/);
    await expect(page.getByRole("alert")).toBeVisible();
  });
});
