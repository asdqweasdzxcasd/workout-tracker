package com.workouttracker.exercise.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/** 운동 통계 응답의 최근 세션 항목. */
@Schema(description = "운동 통계 - 최근 세션 항목")
public record RecentSession(

        @Schema(description = "세션 ID", example = "123") Long sessionId,

        @Schema(description = "수행 일자", example = "2026-05-16") LocalDate performedOn,

        @Schema(description = "해당 세션의 최고 무게 세트 (없으면 null)") TopSet topSet
) {}
