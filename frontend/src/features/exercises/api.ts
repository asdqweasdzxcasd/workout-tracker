/**
 * 운동 종류 마스터 데이터 API.
 *
 * <p>설계: docs/design.md 3.5 GET /exercises
 */
import { api } from "@/lib/api";
import type { ExerciseListResponse, ExerciseResponse } from "@/types/api";

export async function fetchExercises(bodyPart?: string): Promise<ExerciseResponse[]> {
  const { data } = await api.get<ExerciseListResponse>("/exercises", {
    params: bodyPart ? { bodyPart } : undefined,
  });
  return data.content;
}
