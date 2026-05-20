/**
 * 운동 마스터 데이터 E2E.
 *
 * <p>이 프로젝트는 별도 "운동 목록 페이지" 가 없고,
 * 신규 세션 작성 페이지 안의 ExercisePicker(native select + optgroup) 에서만 노출된다.
 * 따라서 /sessions/new 에 진입해서 dropdown 옵션을 검증한다.
 *
 * <p>또한 운동 통계 페이지(/exercises/{id}/stats)는 어떤 운동에 대해서든
 * 조회 가능해야 하므로 BENCH_PRESS(id=1 가정) 접근도 함께 확인한다.
 *
 * <p>시드 데이터: V2__seed_exercises.sql 의 12종.
 */
import { expect, test } from "@playwright/test";

import { signupAndLogin } from "./helpers/auth";
import { addExerciseButton, exerciseSelect } from "./helpers/selectors";

test.describe("운동 마스터 데이터", () => {
  test.beforeEach(async ({ page }) => {
    await signupAndLogin(page);
  });

  test("신규 세션 페이지의 운동 선택 드롭다운에 시드 데이터 12종이 모두 노출된다", async ({ page }) => {
    // When - 신규 세션 진입 + 운동 카드 추가
    await page.goto("/sessions/new");
    // /exercises 응답을 기다려 옵션이 채워진 것을 보장
    const exercisesResp = page.waitForResponse(
      (resp) => resp.url().includes("/exercises") && resp.request().method() === "GET",
    );
    await expect(addExerciseButton(page)).toBeEnabled();
    await addExerciseButton(page).click();
    await exercisesResp;

    // Then - select 안에 시드 데이터의 운동들이 들어있다
    const select = exerciseSelect(page, 0);
    await expect(select).toBeVisible();

    // 시드의 일부 핵심 항목 - 가슴/등/하체에서 하나씩 검증
    await expect(select.locator("option", { hasText: "벤치프레스" })).toHaveCount(1);
    await expect(select.locator("option", { hasText: "데드리프트" })).toHaveCount(1);
    await expect(select.locator("option", { hasText: "바벨 스쿼트" })).toHaveCount(1);
    await expect(select.locator("option", { hasText: "오버헤드프레스" })).toHaveCount(1);
    await expect(select.locator("option", { hasText: "플랭크" })).toHaveCount(1);

    // 12종 + placeholder ("운동을 선택하세요") = 13 옵션
    const optionCount = await select.locator("option").count();
    expect(optionCount).toBe(13);
  });

  test("운동 선택 드롭다운은 BodyPart 별 optgroup 으로 묶여 있다", async ({ page }) => {
    await page.goto("/sessions/new");
    await expect(addExerciseButton(page)).toBeEnabled();
    await addExerciseButton(page).click();

    const select = exerciseSelect(page, 0);
    // 핵심 그룹 라벨이 있어야 한다 (BODY_PART_LABEL 매핑)
    const expectedGroups = ["가슴", "등", "하체", "어깨", "팔", "코어"];
    for (const label of expectedGroups) {
      await expect(select.locator(`optgroup[label="${label}"]`)).toHaveCount(1);
    }
  });

  test("운동 통계 페이지는 기록이 없을 때도 빈 상태를 정상 렌더링한다", async ({ page }) => {
    // BENCH_PRESS - V2 시드의 첫 운동이라 id=1 일 가능성이 매우 높지만
    // 시드 순서에 의존하지 않도록 옵션 value 를 동적으로 읽어서 사용.
    await page.goto("/sessions/new");
    await expect(addExerciseButton(page)).toBeEnabled();
    await addExerciseButton(page).click();

    const select = exerciseSelect(page, 0);
    const benchOption = select.locator("option", { hasText: "벤치프레스" });
    const benchId = await benchOption.getAttribute("value");
    expect(benchId, "벤치프레스 option 의 value(=id) 가 있어야 한다").toBeTruthy();

    // When - 통계 페이지로 진입 (해당 사용자는 아직 운동 기록 없음)
    await page.goto(`/exercises/${benchId}/stats`);

    // Then - PR "기록이 없습니다" 안내 노출
    await expect(page.getByText("Personal Record", { exact: false })).toBeVisible();
    await expect(page.getByText("아직 기록이 없습니다.")).toBeVisible();
    await expect(page.getByText("최근 기록이 없습니다.")).toBeVisible();
  });
});
