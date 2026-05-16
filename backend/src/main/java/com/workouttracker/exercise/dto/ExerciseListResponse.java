package com.workouttracker.exercise.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "운동 종류 목록 응답")
public record ExerciseListResponse(
        @Schema(description = "운동 종류 배열") List<ExerciseResponse> content
) {}
