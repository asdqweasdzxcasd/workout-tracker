"use client";

/**
 * 세션 신규 작성 페이지 (핵심).
 *
 * <p>설계: docs/design.md 4.1 (app)/sessions/new, 4.2 컴포넌트 트리
 *
 * <p>구성:
 * <ul>
 *   <li>react-hook-form useForm + useFieldArray (운동 N개)</li>
 *   <li>각 운동 카드 내부에서 다시 useFieldArray (세트 M개)</li>
 *   <li>운동 종류는 GET /exercises 로 받아 SelectBox 로 노출</li>
 *   <li>저장: POST /sessions → 성공 시 /sessions/[id] 로 이동 + 목록 캐시 무효화</li>
 * </ul>
 *
 * <p>API 매핑:
 * <ul>
 *   <li>orderNo / setNo 는 폼 인덱스 + 1 로 자동 세팅 (사용자 입력 없음)</li>
 *   <li>weightKg 는 BigDecimal → number 직렬화 (백엔드 @Digits 호환)</li>
 * </ul>
 */
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useFieldArray, useForm } from "react-hook-form";

import { Button } from "@/components/ui/button";
import { FieldError, Input, Label, Textarea } from "@/components/ui/input";
import { ExerciseCard } from "@/features/sessions/components/exercise-card";
import { fetchExercises } from "@/features/exercises/api";
import { createSession } from "@/features/sessions/api";
import {
  sessionCreateSchema,
  type SessionCreateFormValues,
} from "@/features/sessions/schemas";
import { extractErrorMessage } from "@/lib/api";
import { todayIso } from "@/lib/format";
import { qk } from "@/lib/query-keys";
import type { SessionCreateRequest } from "@/types/api";

export default function NewSessionPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [serverError, setServerError] = useState<string | null>(null);

  // 운동 종류는 거의 변하지 않는 마스터 데이터 - staleTime 1시간 (docs/design.md 4.3)
  const exercisesQuery = useQuery({
    queryKey: qk.exercises(),
    queryFn: () => fetchExercises(),
    staleTime: 60 * 60 * 1000,
  });

  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<SessionCreateFormValues>({
    resolver: zodResolver(sessionCreateSchema),
    defaultValues: {
      performedOn: todayIso(),
      memo: "",
      exercises: [],
    },
  });

  const {
    fields: exerciseFields,
    append: appendExercise,
    remove: removeExercise,
  } = useFieldArray({ control, name: "exercises" });

  const createMutation = useMutation({
    mutationFn: createSession,
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: qk.sessionsAll });
      router.replace(`/sessions/${data.sessionId}`);
    },
    onError: (error) => {
      setServerError(extractErrorMessage(error, "세션 저장에 실패했습니다."));
    },
  });

  const onSubmit = (values: SessionCreateFormValues) => {
    setServerError(null);
    // orderNo / setNo 를 인덱스 기반으로 채워서 API 페이로드 작성
    const payload: SessionCreateRequest = {
      performedOn: values.performedOn,
      memo: values.memo?.trim() ? values.memo.trim() : undefined,
      exercises: values.exercises.map((ex, exIdx) => ({
        exerciseId: ex.exerciseId,
        orderNo: exIdx + 1,
        sets: ex.sets.map((s, sIdx) => ({
          setNo: sIdx + 1,
          weightKg: s.weightKg,
          reps: s.reps,
        })),
      })),
    };
    createMutation.mutate(payload);
  };

  // 새 운동 카드 - 기본 세트 1개 포함
  const addExercise = () => {
    appendExercise({
      exerciseId: 0,
      sets: [{ weightKg: 0, reps: 0 }],
    });
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-bold text-zinc-900">신규 세션 작성</h1>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        {/* 메타 정보 */}
        <div className="rounded-lg border border-zinc-200 bg-white p-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <Label htmlFor="performedOn" required>
                수행 날짜
              </Label>
              <Input
                id="performedOn"
                type="date"
                max={todayIso()}
                aria-invalid={errors.performedOn ? true : undefined}
                {...register("performedOn")}
              />
              <FieldError message={errors.performedOn?.message} />
            </div>
            <div>
              <Label htmlFor="memo">메모</Label>
              <Textarea
                id="memo"
                rows={2}
                placeholder="가슴/삼두, 컨디션 좋음 등"
                aria-invalid={errors.memo ? true : undefined}
                {...register("memo")}
              />
              <FieldError message={errors.memo?.message} />
            </div>
          </div>
        </div>

        {/* 운동 목록 */}
        {exercisesQuery.isLoading ? (
          <p className="py-6 text-center text-sm text-zinc-500">운동 종류 불러오는 중...</p>
        ) : exercisesQuery.isError ? (
          <div role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
            {extractErrorMessage(exercisesQuery.error, "운동 종류를 불러오지 못했습니다.")}
          </div>
        ) : (
          <div className="space-y-3">
            {exerciseFields.map((field, index) => (
              <ExerciseCard
                key={field.id}
                index={index}
                control={control}
                errors={errors}
                exercises={exercisesQuery.data ?? []}
                onRemove={() => removeExercise(index)}
              />
            ))}

            <Button
              type="button"
              variant="secondary"
              fullWidth
              onClick={addExercise}
              disabled={!exercisesQuery.data || exercisesQuery.data.length === 0}
            >
              <Plus size={16} />
              <span>운동 추가</span>
            </Button>

            {typeof errors.exercises?.message === "string" ? (
              <FieldError message={errors.exercises.message} />
            ) : null}
          </div>
        )}

        {serverError ? (
          <div role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
            {serverError}
          </div>
        ) : null}

        <div className="sticky bottom-0 -mx-4 border-t border-zinc-200 bg-white/95 px-4 py-3 backdrop-blur">
          <div className="mx-auto flex max-w-3xl items-center justify-end gap-2">
            <Button type="button" variant="secondary" onClick={() => router.back()}>
              취소
            </Button>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? "저장 중..." : "세션 저장"}
            </Button>
          </div>
        </div>
      </form>
    </div>
  );
}
