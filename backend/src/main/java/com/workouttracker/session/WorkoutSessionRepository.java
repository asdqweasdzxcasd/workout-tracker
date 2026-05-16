package com.workouttracker.session;

import com.workouttracker.session.dto.SessionListProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, Long> {

    /**
     * 소유권 검증용 - 본인의 세션만 조회.
     * 다른 사용자의 세션 ID 를 받아도 빈 Optional 반환 → 컨트롤러가 404 로 변환.
     */
    Optional<WorkoutSession> findByIdAndUserId(Long id, Long userId);

    /**
     * 상세 조회용 - exercises 만 fetch join + sets 는 @BatchSize(50) 로 N+1 회피.
     *
     * <p>2단계 컬렉션 동시 fetch join 은 MultipleBagFetchException 을 일으키므로
     * SessionExercise.sets 에 @BatchSize 를 두어 IN 절 한 번으로 일괄 로딩하는 방식 채택.
     * (Hibernate 모범 사례)
     *
     * <p>중복 row 제거를 위해 {@code SELECT DISTINCT} 사용.
     */
    @Query("""
            SELECT DISTINCT s
            FROM WorkoutSession s
            LEFT JOIN FETCH s.exercises se
            WHERE s.id = :id AND s.userId = :userId
            """)
    Optional<WorkoutSession> findDetailByIdAndUserId(
            @Param("id") Long id,
            @Param("userId") Long userId);

    /**
     * 세션 목록 + 집계 단일 쿼리.
     *
     * <p>exerciseCount = DISTINCT se.id 카운트<br>
     * totalSets       = es.id 카운트<br>
     * totalVolume     = SUM(es.weightKg * es.reps)<br>
     *
     * <p>정렬은 코드에서 강제: performedOn DESC, id DESC
     * (sort 파라미터 미지원 - 설계 3.6).
     *
     * <p>Pageable 에서 page, size 만 사용되고 정렬은 무시됨.
     * (Pageable.unsorted() 권장 - 서비스 레이어에서 PageRequest.of(page, size) 만 사용)
     */
    @Query("""
            SELECT new com.workouttracker.session.dto.SessionListProjection(
                s.id,
                s.performedOn,
                s.memo,
                COUNT(DISTINCT se.id),
                COUNT(es.id),
                COALESCE(SUM(es.weightKg * es.reps), 0)
            )
            FROM WorkoutSession s
            LEFT JOIN s.exercises se
            LEFT JOIN se.sets es
            WHERE s.userId = :userId
            GROUP BY s.id, s.performedOn, s.memo
            ORDER BY s.performedOn DESC, s.id DESC
            """)
    Slice<SessionListProjection> findSessionList(
            @Param("userId") Long userId,
            Pageable pageable);
}
