"use client";

/**
 * 운동 종류 선택 드롭다운.
 *
 * <p>설계: docs/design.md 4.2 ExercisePicker
 *
 * <p>MVP 는 native select 로 단순 구현. 부위(body part)별로 optgroup 묶음.
 */
import { Select } from "@/components/ui/input";
import type { ExerciseResponse } from "@/types/api";

const BODY_PART_LABEL: Record<string, string> = {
  CHEST: "가슴",
  BACK: "등",
  LEG: "하체",
  SHOULDER: "어깨",
  ARM: "팔",
  CORE: "코어",
};

type Props = {
  id?: string;
  exercises: ExerciseResponse[];
  value: number | null;
  onChange: (value: number) => void;
  ariaInvalid?: boolean;
};

export function ExercisePicker({ id, exercises, value, onChange, ariaInvalid }: Props) {
  // 부위별 그룹핑 (동일 부위 운동들을 optgroup 으로 묶어 가독성 향상)
  const grouped = new Map<string, ExerciseResponse[]>();
  for (const ex of exercises) {
    const list = grouped.get(ex.bodyPart) ?? [];
    list.push(ex);
    grouped.set(ex.bodyPart, list);
  }

  return (
    <Select
      id={id}
      value={value ?? ""}
      aria-invalid={ariaInvalid}
      onChange={(e) => onChange(Number(e.target.value))}
    >
      <option value="" disabled>
        운동을 선택하세요
      </option>
      {Array.from(grouped.entries()).map(([bodyPart, items]) => (
        <optgroup key={bodyPart} label={BODY_PART_LABEL[bodyPart] ?? bodyPart}>
          {items.map((ex) => (
            <option key={ex.id} value={ex.id}>
              {ex.nameKo}
            </option>
          ))}
        </optgroup>
      ))}
    </Select>
  );
}
