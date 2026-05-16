/**
 * 세션 작성 폼 zod 스키마.
 *
 * <p>설계: docs/design.md 3.5 POST /sessions
 *
 * <p>백엔드 SessionCreateRequest 의 Bean Validation 과 동일 규칙으로 즉시 피드백한다:
 * <ul>
 *   <li>performedOn: 오늘 또는 과거</li>
 *   <li>memo: 0~500자</li>
 *   <li>exercises: 1개 이상</li>
 *   <li>sets: 1개 이상</li>
 *   <li>weightKg: 0 이상, 9999.99 이하, 소수 2자리</li>
 *   <li>reps: 1~999</li>
 * </ul>
 *
 * <p>orderNo / setNo 는 폼 인덱스로 자동 채워서 사용자가 직접 입력하지 않는다.
 */
import { z } from "zod";

import { todayIso } from "@/lib/format";

export const setSchema = z.object({
  weightKg: z
    .number({ message: "무게를 입력하세요." })
    .min(0, "무게는 0 이상이어야 합니다.")
    .max(9999.99, "무게는 9999.99 이하여야 합니다."),
  reps: z
    .number({ message: "횟수를 입력하세요." })
    .int("정수만 입력 가능합니다.")
    .min(1, "1회 이상이어야 합니다.")
    .max(999, "999회 이하여야 합니다."),
});

export const sessionExerciseSchema = z.object({
  exerciseId: z
    .number({ message: "운동을 선택하세요." })
    .int()
    .min(1, "운동을 선택하세요."),
  sets: z.array(setSchema).min(1, "세트를 1개 이상 추가하세요."),
});

export const sessionCreateSchema = z.object({
  performedOn: z
    .string()
    .min(1, "수행 날짜를 선택하세요.")
    .refine((iso) => iso <= todayIso(), { message: "미래 날짜는 입력할 수 없습니다." }),
  memo: z.string().max(500, "메모는 500자 이내여야 합니다.").optional(),
  exercises: z.array(sessionExerciseSchema).min(1, "운동을 1개 이상 추가하세요."),
});

export type SessionCreateFormValues = z.infer<typeof sessionCreateSchema>;
export type SessionExerciseFormValues = z.infer<typeof sessionExerciseSchema>;
export type SetFormValues = z.infer<typeof setSchema>;
