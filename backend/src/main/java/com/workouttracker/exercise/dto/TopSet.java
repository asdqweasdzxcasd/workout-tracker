package com.workouttracker.exercise.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/** 운동 통계 응답에서 한 세션의 최고 무게/횟수 한 쌍. */
@Schema(description = "한 세션 안에서 특정 운동의 최고 무게 세트")
public record TopSet(

        @Schema(description = "무게(kg)", example = "90.0") BigDecimal weightKg,

        @Schema(description = "횟수", example = "5") Integer reps
) {}
