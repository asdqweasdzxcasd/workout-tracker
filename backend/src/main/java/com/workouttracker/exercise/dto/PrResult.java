package com.workouttracker.exercise.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 운동 PR(Personal Record) 추출용 Projection.
 *
 * <p>출처: docs/design.md 3.5 GET /exercises/{id}/stats
 *
 * <p>{@code ExerciseSet ↔ SessionExercise ↔ WorkoutSession} 조인 결과에서
 * (최고 무게, 해당 세션의 수행 일자) 한 행만 가져온다.
 */
public record PrResult(
        BigDecimal weightKg,
        LocalDate performedOn
) {}
