package com.workouttracker.session.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 세션 생성 요청 DTO.
 *
 * <p>출처: docs/design.md 3.5 POST /sessions
 *
 * <p>Bean Validation 으로 잡는 것:
 * <ul>
 *   <li>performedOn 필수 + 과거/오늘</li>
 *   <li>memo 길이 0~500</li>
 *   <li>exercises 최소 1개</li>
 *   <li>각 운동의 sets 최소 1개</li>
 *   <li>weightKg >= 0 / reps >= 1 등</li>
 * </ul>
 *
 * <p>서비스 레벨에서 추가 검증:
 * <ul>
 *   <li>exerciseId 존재 & is_active</li>
 *   <li>orderNo / setNo 중복 여부</li>
 * </ul>
 */
@Schema(description = "세션 생성 요청")
public record SessionCreateRequest(

        @Schema(description = "수행 날짜 (YYYY-MM-DD)", example = "2026-05-16")
        @NotNull
        @PastOrPresent
        LocalDate performedOn,

        @Schema(description = "메모 (선택, 최대 500자)", example = "가슴/삼두")
        @Size(max = 500)
        String memo,

        @Schema(description = "운동 목록 (1개 이상)")
        @NotNull
        @NotEmpty(message = "exercises 는 최소 1개 이상이어야 합니다.")
        @Valid
        List<SessionExerciseCreate> exercises
) {

    /** 세션에 포함되는 운동 1개 (운동 종류 + 정렬 순서 + 세트 배열) */
    @Schema(description = "세션 운동 항목")
    public record SessionExerciseCreate(

            @Schema(description = "운동 ID", example = "1")
            @NotNull
            @Min(value = 1)
            Long exerciseId,

            @Schema(description = "정렬 순서 (1부터)", example = "1")
            @NotNull
            @Min(value = 1)
            @Max(value = 50)
            Integer orderNo,

            @Schema(description = "세트 목록 (1개 이상)")
            @NotNull
            @NotEmpty(message = "sets 는 최소 1개 이상이어야 합니다.")
            @Valid
            List<ExerciseSetCreate> sets
    ) {}

    /** 세트 1개 (set 번호 + 무게 + 횟수) */
    @Schema(description = "세트 입력")
    public record ExerciseSetCreate(

            @Schema(description = "세트 번호 (1부터)", example = "1")
            @NotNull
            @Min(value = 1)
            @Max(value = 50)
            Integer setNo,

            @Schema(description = "무게 (kg, 소수 둘째 자리까지)", example = "60.0")
            @NotNull
            @DecimalMin(value = "0.0", inclusive = true, message = "무게는 0 이상이어야 합니다.")
            @DecimalMax(value = "9999.99", inclusive = true)
            @Digits(integer = 4, fraction = 2)
            BigDecimal weightKg,

            @Schema(description = "반복 횟수 (1 이상)", example = "10")
            @NotNull
            @Min(value = 1)
            @Max(value = 999)
            Integer reps
    ) {}
}
