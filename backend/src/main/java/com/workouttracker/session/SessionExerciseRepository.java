package com.workouttracker.session;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * SessionExercise 기본 리포지토리.
 *
 * <p>대부분의 조회는 WorkoutSession 의 fetch join 으로 처리되며,
 * 본 리포지토리는 향후 단건 작업/배치 작업 확장 포인트로 유지.
 */
public interface SessionExerciseRepository extends JpaRepository<SessionExercise, Long> {
}
