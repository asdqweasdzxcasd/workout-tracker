"use client";

/**
 * 세션 작성 폼의 운동 1개 카드.
 *
 * <p>설계: docs/design.md 4.2 ExerciseSection / SetRow
 *
 * <p>useFieldArray 가 중첩(`exercises.{i}.sets`) 형태로 동작하도록
 * 본 컴포넌트에서 부모(useForm)의 control 을 받아 사용한다.
 */
import { Trash2, Plus } from "lucide-react";
import { Controller, useFieldArray, type Control, type FieldErrors } from "react-hook-form";

import { Button } from "@/components/ui/button";
import { FieldError, Input, Label } from "@/components/ui/input";
import { ExercisePicker } from "./exercise-picker";
import type { SessionCreateFormValues } from "@/features/sessions/schemas";
import type { ExerciseResponse } from "@/types/api";

type Props = {
  index: number;
  control: Control<SessionCreateFormValues>;
  errors: FieldErrors<SessionCreateFormValues>;
  exercises: ExerciseResponse[];
  onRemove: () => void;
};

export function ExerciseCard({ index, control, errors, exercises, onRemove }: Props) {
  const { fields, append, remove } = useFieldArray({
    control,
    name: `exercises.${index}.sets`,
  });

  const exerciseError = errors.exercises?.[index];

  return (
    <div className="rounded-lg border border-zinc-200 bg-white p-4 shadow-sm">
      <div className="flex items-end justify-between gap-3">
        <div className="flex-1">
          <Label htmlFor={`exercise-${index}`} required>
            운동 #{index + 1}
          </Label>
          <Controller
            control={control}
            name={`exercises.${index}.exerciseId`}
            render={({ field }) => (
              <ExercisePicker
                id={`exercise-${index}`}
                exercises={exercises}
                value={field.value && field.value > 0 ? field.value : null}
                onChange={field.onChange}
                ariaInvalid={exerciseError?.exerciseId ? true : undefined}
              />
            )}
          />
          <FieldError message={exerciseError?.exerciseId?.message} />
        </div>
        <Button
          type="button"
          variant="ghost"
          onClick={onRemove}
          aria-label={`운동 ${index + 1} 삭제`}
        >
          <Trash2 size={16} className="text-red-500" />
        </Button>
      </div>

      <div className="mt-4 space-y-2">
        <div className="grid grid-cols-[24px_1fr_1fr_32px] items-center gap-2 text-xs font-medium text-zinc-500">
          <span>#</span>
          <span>무게 (kg)</span>
          <span>횟수</span>
          <span />
        </div>

        {fields.map((field, setIndex) => (
          <SetRow
            key={field.id}
            exerciseIndex={index}
            setIndex={setIndex}
            control={control}
            errors={errors}
            onRemove={() => remove(setIndex)}
            canRemove={fields.length > 1}
          />
        ))}

        <Button
          type="button"
          variant="secondary"
          onClick={() => append({ weightKg: 0, reps: 0 })}
        >
          <Plus size={16} />
          <span>세트 추가</span>
        </Button>

        {typeof exerciseError?.sets?.message === "string" ? (
          <FieldError message={exerciseError.sets.message} />
        ) : null}
      </div>
    </div>
  );
}

type SetRowProps = {
  exerciseIndex: number;
  setIndex: number;
  control: Control<SessionCreateFormValues>;
  errors: FieldErrors<SessionCreateFormValues>;
  onRemove: () => void;
  canRemove: boolean;
};

function SetRow({ exerciseIndex, setIndex, control, errors, onRemove, canRemove }: SetRowProps) {
  const setError = errors.exercises?.[exerciseIndex]?.sets?.[setIndex];

  return (
    <div>
      <div className="grid grid-cols-[24px_1fr_1fr_32px] items-center gap-2">
        <span className="text-sm font-medium text-zinc-600">{setIndex + 1}</span>

        <Controller
          control={control}
          name={`exercises.${exerciseIndex}.sets.${setIndex}.weightKg`}
          render={({ field }) => (
            <Input
              type="number"
              step="0.5"
              min="0"
              inputMode="decimal"
              value={Number.isFinite(field.value) ? field.value : ""}
              onChange={(e) => field.onChange(e.target.value === "" ? Number.NaN : Number(e.target.value))}
              aria-invalid={setError?.weightKg ? true : undefined}
            />
          )}
        />

        <Controller
          control={control}
          name={`exercises.${exerciseIndex}.sets.${setIndex}.reps`}
          render={({ field }) => (
            <Input
              type="number"
              step="1"
              min="1"
              inputMode="numeric"
              value={Number.isFinite(field.value) ? field.value : ""}
              onChange={(e) => field.onChange(e.target.value === "" ? Number.NaN : Number(e.target.value))}
              aria-invalid={setError?.reps ? true : undefined}
            />
          )}
        />

        <button
          type="button"
          onClick={onRemove}
          disabled={!canRemove}
          aria-label={`세트 ${setIndex + 1} 삭제`}
          className="rounded p-1 text-zinc-400 hover:bg-zinc-100 hover:text-red-500 disabled:cursor-not-allowed disabled:opacity-30"
        >
          <Trash2 size={14} />
        </button>
      </div>
      {(setError?.weightKg?.message || setError?.reps?.message) && (
        <p role="alert" className="mt-1 text-xs text-red-600">
          {setError?.weightKg?.message ?? setError?.reps?.message}
        </p>
      )}
    </div>
  );
}
