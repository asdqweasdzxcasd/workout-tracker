package com.workouttracker.exercise.dto;

import com.workouttracker.exercise.Exercise;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "운동 종류")
public record ExerciseResponse(
        @Schema(description = "운동 ID", example = "1") Long id,
        @Schema(description = "운동 코드", example = "BENCH_PRESS") String code,
        @Schema(description = "한글명", example = "벤치프레스") String nameKo,
        @Schema(description = "영문명", example = "Bench Press") String nameEn,
        @Schema(description = "부위 (CHEST/BACK/LEG/SHOULDER/ARM/CORE)", example = "CHEST") String bodyPart,
        @Schema(description = "카테고리 (COMPOUND/ISOLATION)", example = "COMPOUND") String category
) {
    public static ExerciseResponse from(Exercise e) {
        return new ExerciseResponse(
                e.getId(),
                e.getCode(),
                e.getNameKo(),
                e.getNameEn(),
                e.getBodyPart(),
                e.getCategory()
        );
    }
}
