package com.workouttracker.session;

import com.workouttracker.exercise.dto.PrResult;
import com.workouttracker.exercise.dto.RecentSessionRow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * ExerciseSet 리포지토리.
 *
 * <p>PR(Personal Record) / 최근 세션 집계 쿼리를 보유한다.
 * 인덱스 활용:
 * <ul>
 *   <li>{@code idx_session_exercises_exercise} - exerciseId 필터링</li>
 *   <li>{@code idx_exercise_sets_weight} - weightKg DESC 정렬</li>
 * </ul>
 */
public interface ExerciseSetRepository extends JpaRepository<ExerciseSet, Long> {

    /**
     * 본인 + 특정 운동 기준 최고 무게 세트의 (weightKg, performedOn) 한 행 추출.
     *
     * <p>Pageable.of(0, 1) 로 첫 행만 가져온다. 동률이면 가장 최근 performedOn 이 우선.
     * 결과가 비어있으면 호출자에서 null PR 로 처리.
     */
    @Query("""
            SELECT new com.workouttracker.exercise.dto.PrResult(
                es.weightKg,
                ws.performedOn
            )
            FROM ExerciseSet es
            JOIN es.sessionExercise se
            JOIN se.session ws
            WHERE ws.userId = :userId
              AND se.exerciseId = :exerciseId
            ORDER BY es.weightKg DESC, ws.performedOn DESC, es.id DESC
            """)
    List<PrResult> findTopWeight(
            @Param("userId") Long userId,
            @Param("exerciseId") Long exerciseId,
            Pageable pageable);

    /**
     * 특정 운동의 최근 세션별 최고 무게 추출 (reps 제외).
     *
     * <p>세션 단위로 GROUP BY 하여 (sessionId, performedOn, 최고 무게) 를 가져온다.
     * reps 는 별도 호출 ({@link #findTopRepsForSessions})로 한 번에 조회하여 매핑한다.
     *
     * <p>Pageable 로 최근 N개 제한 (보통 5). 정렬: performedOn DESC, sessionId DESC.
     */
    @Query("""
            SELECT new com.workouttracker.exercise.dto.RecentSessionRow(
                ws.id,
                ws.performedOn,
                MAX(es.weightKg)
            )
            FROM ExerciseSet es
            JOIN es.sessionExercise se
            JOIN se.session ws
            WHERE ws.userId = :userId
              AND se.exerciseId = :exerciseId
            GROUP BY ws.id, ws.performedOn
            ORDER BY ws.performedOn DESC, ws.id DESC
            """)
    List<RecentSessionRow> findRecentSessions(
            @Param("userId") Long userId,
            @Param("exerciseId") Long exerciseId,
            Pageable pageable);

    /**
     * 여러 세션에 대해 (sessionId, 최고 무게의 reps) 매핑 조회.
     *
     * <p>위 {@link #findRecentSessions} 의 결과에서 reps 를 채워넣을 때 사용한다.
     * 세션마다 같은 운동의 최고 무게 세트 중 가장 높은 무게의 reps 한 행씩.
     *
     * <p>JPQL window function 미지원으로 인해 한 쿼리로 깔끔하게 못 풀어
     * 서비스 레이어에서 일괄 조회 후 Java 로 reduce 한다. 본 메서드는 그 일괄 조회 단계.
     *
     * <p>반환: (sessionId, weightKg, reps) 세트들. 호출자가 sessionId 그룹별로 첫 weightKg DESC 행만 사용.
     */
    @Query("""
            SELECT se.session.id AS sessionId,
                   es.weightKg   AS weightKg,
                   es.reps       AS reps
            FROM ExerciseSet es
            JOIN es.sessionExercise se
            WHERE se.exerciseId = :exerciseId
              AND se.session.id IN :sessionIds
            ORDER BY se.session.id ASC, es.weightKg DESC, es.id DESC
            """)
    List<Object[]> findSetsForSessions(
            @Param("exerciseId") Long exerciseId,
            @Param("sessionIds") Collection<Long> sessionIds);
}
