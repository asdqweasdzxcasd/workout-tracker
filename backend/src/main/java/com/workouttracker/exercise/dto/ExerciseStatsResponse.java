package com.workouttracker.exercise.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 운동별 PR / 최근 기록 응답.
 *
 * <p>출처: docs/design.md 3.5 GET /exercises/{id}/stats
 *
 * <p>운동 기록이 없으면 PR 필드는 null, recentSessions 는 빈 배열.
 */
@Schema(description = "운동 통계 응답")
public record ExerciseStatsResponse(

        @Schema(description = "운동 ID", example = "1") Long exerciseId,

        @Schema(description = "운동명(한글)", example = "벤치프레스") String name,

        @Schema(description = "최고 무게 (kg). 기록 없으면 null", example = "100.0")
        BigDecimal personalRecordKg,

        @Schema(description = "PR 달성 일자. 기록 없으면 null", example = "2026-05-10")
        LocalDate personalRecordDate,

        @Schema(description = "최근 세션 목록 (최대 5건, 수행 일자 내림차순)")
        List<RecentSession> recentSessions
) {}
