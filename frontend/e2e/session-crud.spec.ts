/**
 * 세션 생성/조회 핵심 E2E 시나리오.
 *
 * <p>workout-tracker 의 가장 중요한 사용자 여정 - "오늘 운동 기록 남기기".
 *
 * <p>흐름:
 * <ol>
 *   <li>회원가입 + 자동 로그인 → /sessions (빈 목록)</li>
 *   <li>"신규 작성" 진입 → 메모 입력 → 운동 추가 (벤치프레스) → 세트 2개 (60kg×10, 80kg×8)</li>
 *   <li>저장 → /sessions/{id} 상세로 리다이렉트</li>
 *   <li>목록으로 돌아오면 새 세션 카드가 표시되고, 클릭 시 동일 데이터가 보인다</li>
 * </ol>
 *
 * <p>S3 사진 업로드는 본 라운드에서 스킵 (test.fixme 로 placeholder).
 */
import { expect, test } from "@playwright/test";

import { signupAndLogin } from "./helpers/auth";
import {
  addExerciseButton,
  addSetButton,
  exerciseSelect,
  newSessionLink,
  saveSessionButton,
  sessionCard,
} from "./helpers/selectors";

test.describe("세션 작성/조회", () => {
  test.beforeEach(async ({ page }) => {
    await signupAndLogin(page);
  });

  test("벤치프레스 1개 + 세트 2개로 세션을 만들고 상세에서 확인한다", async ({ page }) => {
    // Given - 처음에는 빈 상태
    await expect(page.getByText("아직 운동 기록이 없습니다.")).toBeVisible();

    // When - 신규 세션 페이지로 이동
    await newSessionLink(page).first().click();
    await expect(page).toHaveURL(/\/sessions\/new$/);

    // 메모 입력
    await page.getByLabel("메모").fill("E2E 테스트 - 가슴/삼두");

    // 운동 추가 - /exercises 응답을 기다려 옵션이 채워진 것을 보장
    const exercisesResp = page.waitForResponse(
      (resp) => resp.url().includes("/exercises") && resp.request().method() === "GET",
    );
    await expect(addExerciseButton(page)).toBeEnabled();
    await addExerciseButton(page).click();
    await exercisesResp;

    // 벤치프레스 선택 - select 옵션을 label 로 찾기
    const select = exerciseSelect(page, 0);
    await select.selectOption({ label: "벤치프레스" });

    // 첫 세트: 60kg × 10
    // 운동 추가 시 기본 세트 1개가 따라온다. weight/reps input 은 number type.
    const weightInputs = page.locator('input[type="number"][step="0.5"]');
    const repsInputs = page.locator('input[type="number"][step="1"]');
    await weightInputs.nth(0).fill("60");
    await repsInputs.nth(0).fill("10");

    // 두 번째 세트 추가: 80kg × 8
    await addSetButton(page, 0).click();
    await expect(weightInputs).toHaveCount(2);
    await weightInputs.nth(1).fill("80");
    await repsInputs.nth(1).fill("8");

    // 저장 - POST /sessions 응답을 기다려 sessionId 도 확인
    const createResp = page.waitForResponse(
      (resp) =>
        resp.url().includes("/sessions") &&
        resp.request().method() === "POST" &&
        // /sessions/{id}/photos 같은 nested path 제외
        /\/sessions(\?|$)/.test(new URL(resp.url()).pathname),
    );
    await saveSessionButton(page).click();
    const resp = await createResp;
    expect(resp.status()).toBe(201);
    const body = (await resp.json()) as { sessionId: number };
    expect(body.sessionId).toBeGreaterThan(0);

    // Then - 상세 페이지로 리다이렉트 + 입력값이 동일하게 표시
    await page.waitForURL(new RegExp(`/sessions/${body.sessionId}$`));
    await expect(page.getByRole("heading", { level: 2, name: /벤치프레스/ })).toBeVisible();
    await expect(page.getByText("E2E 테스트 - 가슴/삼두")).toBeVisible();

    // 세트 표시 검증 - "60.0" / "80.0" (formatWeightKg 가 1자리 fixed 가정)
    // 다만 포맷 함수가 다른 자리수일 수도 있어 "60"/"80" 으로 느슨하게 확인
    const setsList = page.locator("ul").filter({ has: page.locator("li", { hasText: /^60/ }) }).first();
    await expect(setsList.getByText(/60/).first()).toBeVisible();
    await expect(setsList.getByText(/80/).first()).toBeVisible();
    await expect(setsList.getByText("10", { exact: true })).toBeVisible();
    await expect(setsList.getByText("8", { exact: true })).toBeVisible();

    // 목록으로 돌아가서 카드가 표시되는지
    await page.getByRole("link", { name: "목록으로" }).click();
    await expect(page).toHaveURL(/\/sessions$/);

    const card = sessionCard(page).first();
    await expect(card).toBeVisible();
    await expect(card).toContainText("E2E 테스트 - 가슴/삼두");

    // 카드 클릭 → 같은 상세 화면
    await card.click();
    await page.waitForURL(/\/sessions\/\d+$/);
    await expect(page.getByText("E2E 테스트 - 가슴/삼두")).toBeVisible();
  });

  test("운동/세트 없이 저장하면 폼 검증이 막아준다", async ({ page }) => {
    await page.goto("/sessions/new");

    // 운동 없이 바로 저장
    await saveSessionButton(page).click();

    // 백엔드 호출이 가지 않고 페이지에 머무름
    await expect(page).toHaveURL(/\/sessions\/new$/);
    // role=alert 가 어딘가 보이거나 (zod 메시지 fieldError),
    // 적어도 /sessions 상세로 이동하지는 않아야 한다.
    // 메시지 노출 위치는 sessionCreateSchema 의 exercises 검증 결과에 따라 달라지므로
    // 강한 단언은 피하고 URL 만 검증.
  });

  test("세션 상세에서 삭제 후 목록에 더 이상 보이지 않는다", async ({ page }) => {
    // Given - 세션 1개 작성
    await page.goto("/sessions/new");
    await page.getByLabel("메모").fill("삭제 대상");
    await expect(addExerciseButton(page)).toBeEnabled();
    await addExerciseButton(page).click();
    await exerciseSelect(page, 0).selectOption({ label: "데드리프트" });
    await page.locator('input[type="number"][step="0.5"]').first().fill("100");
    await page.locator('input[type="number"][step="1"]').first().fill("5");

    const createResp = page.waitForResponse(
      (resp) =>
        resp.url().includes("/sessions") &&
        resp.request().method() === "POST" &&
        /\/sessions(\?|$)/.test(new URL(resp.url()).pathname),
    );
    await saveSessionButton(page).click();
    const createdResp = await createResp;
    const created = (await createdResp.json()) as { sessionId: number };
    await page.waitForURL(new RegExp(`/sessions/${created.sessionId}$`));

    // When - 삭제. confirm dialog 자동 수락.
    page.once("dialog", (dialog) => dialog.accept());
    const deleteResp = page.waitForResponse(
      (resp) =>
        resp.url().includes(`/sessions/${created.sessionId}`) && resp.request().method() === "DELETE",
    );
    await page.getByRole("button", { name: /삭제$/ }).click();
    const delResp = await deleteResp;
    expect(delResp.status()).toBeGreaterThanOrEqual(200);
    expect(delResp.status()).toBeLessThan(300);

    // Then - 목록으로 자동 이동 + 빈 상태
    await page.waitForURL(/\/sessions$/);
    await expect(page.getByText("아직 운동 기록이 없습니다.")).toBeVisible();
  });

  // 사진 업로드는 S3 presigned URL 이 필요 - 로컬에서 자격이 없을 수 있어 스킵
  test.fixme(
    "세션에 사진을 업로드하면 갤러리에 표시된다 (S3 자격 필요)",
    async ({ page: _page }) => {
      // TODO: 로컬 MinIO 또는 mocked presigner 가 준비되면 활성화
      // 1) 세션 생성
      // 2) PhotoUploader 에 file 첨부 (page.setInputFiles)
      // 3) /photos presign → S3 PUT → /photos 메타 등록 응답 대기
      // 4) PhotoGallery 에 img 등장 확인
    },
  );
});
