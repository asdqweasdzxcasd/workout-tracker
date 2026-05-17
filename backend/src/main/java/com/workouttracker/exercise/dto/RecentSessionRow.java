package com.workouttracker.exercise.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 운동별 최근 세션 행 Projection.
 *
 * <p>JPQL 집계로 (sessionId, performedOn, 최고 무게) 만 가져온다.
 * reps 는 서비스 레이어에서 별도 일괄 조회 결과로 매핑된다.
 */
public record RecentSessionRow(
        Long sessionId,
        LocalDate performedOn,
        BigDecimal topWeightKg
) {}
