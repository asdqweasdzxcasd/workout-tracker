package com.workouttracker.session.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 세션 목록 + 집계 단일 쿼리(JPQL)용 Projection.
 *
 * <p>출처: docs/design.md 3.5 GET /sessions
 *
 * <p>JPQL 의 {@code new ...SessionListProjection(...)} 생성자 매핑에 사용된다.
 * COUNT/SUM 등의 집계 결과 타입을 명확히 하기 위해 JPQL 에서 명시적 캐스팅을 사용한다.
 *
 * <p>주의: JPA 에서 COUNT 의 반환 타입은 Long. SUM(NUMERIC) 은 BigDecimal.
 */
public record SessionListProjection(
        Long id,
        LocalDate performedOn,
        String memo,
        long exerciseCount,
        long totalSets,
        BigDecimal totalVolume,
        long photoCount
) {}
