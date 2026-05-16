package com.workouttracker.session;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * ExerciseSet 기본 리포지토리.
 *
 * <p>현재 세트 단위 단일 조회/수정 API 는 없으나, 향후 운동별 PR 집계(Day 5)에
 * 활용될 수 있도록 빈 인터페이스로 유지.
 */
public interface ExerciseSetRepository extends JpaRepository<ExerciseSet, Long> {
}
