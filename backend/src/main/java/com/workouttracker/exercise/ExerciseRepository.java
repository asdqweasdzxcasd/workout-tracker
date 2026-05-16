package com.workouttracker.exercise;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    /** 전체 활성 운동 목록 (id 오름차순) */
    List<Exercise> findAllByIsActiveTrueOrderByIdAsc();

    /** 부위별 활성 운동 목록 (id 오름차순) */
    List<Exercise> findAllByBodyPartAndIsActiveTrueOrderByIdAsc(String bodyPart);
}
