/**
 * Playwright 설정.
 *
 * <p>설계: Phase 7 E2E 자동화 (workout-tracker).
 *
 * <p>핵심 결정:
 * <ul>
 *   <li>테스트 디렉토리는 `e2e/` - Next.js 의 일반 `__tests__` / `tests/` 와 충돌 방지.</li>
 *   <li>webServer 자동 기동 + 재사용 - 로컬 개발자가 이미 띄운 dev 서버를 그대로 활용.</li>
 *   <li>스크린샷/트레이스는 실패 시에만 - CI 디스크/시간 절약.</li>
 *   <li>현재는 Chromium 단일 브라우저. webkit/firefox 는 필요 시 projects 에 추가.</li>
 * </ul>
 */
import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",

  // 한 테스트의 최대 실행 시간 (signup → 자동 login → 세션 작성까지 직렬 흐름에 여유).
  // 로컬 Spring Boot 콜드스타트 + BCrypt 비용 고려 45초로 잡음.
  timeout: 45_000,
  expect: {
    // expect().toHaveText 등 web-first assertion 의 재시도 한계
    timeout: 5_000,
  },

  // 로컬에서는 빠른 피드백 우선이지만 백엔드 부담을 고려해
  // 파일 간 병렬은 끄고 파일 내부 테스트는 순차 실행 (signup 가 BCrypt 부하 큼).
  fullyParallel: false,

  // CI 에서는 retry 1회 - flaky 발견을 위해 로컬에서는 0회
  retries: process.env.CI ? 1 : 0,

  // 한 번에 하나의 워커만 - 가벼운 로컬 DB / Spring Boot 부하 회피.
  // 향후 백엔드가 안정화되면 undefined 로 풀어 병렬 가속 가능.
  workers: 1,

  // 실패 시 .only 가 남아있는 것을 잡기 위해 forbidOnly
  forbidOnly: !!process.env.CI,

  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : [["list"], ["html", { open: "never" }]],

  use: {
    baseURL: "http://localhost:3000",
    // 추적: 첫 retry 에서만 - 실패 디버깅용
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    // 한국어 페이지 - 영어 alert 등 잘못된 매칭 방지
    locale: "ko-KR",
    timezoneId: "Asia/Seoul",
  },

  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
    // 추후 추가 시 주석 해제
    // { name: "webkit", use: { ...devices["Desktop Safari"] } },
    // { name: "firefox", use: { ...devices["Desktop Firefox"] } },
  ],

  // dev 서버 자동 기동. 이미 떠 있으면 재사용 (로컬 DX).
  // CI 에서는 항상 새로 띄워 깨끗한 상태 보장.
  webServer: {
    command: "npm run dev",
    url: "http://localhost:3000",
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
    stdout: "pipe",
    stderr: "pipe",
  },
});
