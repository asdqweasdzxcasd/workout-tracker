/**
 * 자주 쓰이는 셀렉터 모음.
 *
 * <p>원칙: 접근성 기반 (role/label) 우선. data-testid 는 최후 수단.
 *
 * <p>본 프로젝트는 form 의 모든 input 에 <Label htmlFor> 가 붙어 있어
 * getByLabel 만으로 거의 모든 필드를 선택할 수 있다.
 */
import type { Page, Locator } from "@playwright/test";

/** 세션 카드 (목록 항목) - link role 로 노출됨. href 패턴으로 식별. */
export function sessionCard(page: Page): Locator {
  return page.locator('a[href^="/sessions/"]:not([href="/sessions/new"])');
}

/** 신규 작성 버튼 ("신규 작성" 텍스트가 들어있는 link). */
export function newSessionLink(page: Page): Locator {
  return page.getByRole("link", { name: /신규 작성|지금 기록하기/ });
}

/** 신규 세션 폼의 N 번째 운동 카드의 select. */
export function exerciseSelect(page: Page, index: number): Locator {
  return page.locator(`#exercise-${index}`);
}

/** 신규 세션 폼의 "운동 추가" 버튼. */
export function addExerciseButton(page: Page): Locator {
  return page.getByRole("button", { name: "운동 추가" });
}

/** 신규 세션 폼의 "세트 추가" 버튼 - 운동 카드 별로 존재하므로 nth 로 선택. */
export function addSetButton(page: Page, exerciseIndex = 0): Locator {
  return page.getByRole("button", { name: "세트 추가" }).nth(exerciseIndex);
}

/** 폼 저장 버튼. */
export function saveSessionButton(page: Page): Locator {
  return page.getByRole("button", { name: "세션 저장" });
}
