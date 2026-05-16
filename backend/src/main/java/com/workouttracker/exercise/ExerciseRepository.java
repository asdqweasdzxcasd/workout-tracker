package com.workouttracker.exercise;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    /** 전체 활성 운동 목록 (id 오름차순) */
    List<Exercise> findAllByIsActiveTrueOrderByIdAsc();

    /** 부위별 활성 운동 목록 (id 오름차순) */
    List<Exercise> findAllByBodyPartAndIsActiveTrueOrderByIdAsc(String bodyPart);

    /** 다중 id 활성 운동 조회 (세션 생성 시 exerciseId 일괄 검증용) */
    List<Exercise> findAllByIdInAndIsActiveTrue(Collection<Long> ids);

    /** 다중 id 운동 조회 (상세 응답에서 운동 정보 매핑용) */
    List<Exercise> findAllByIdIn(Collection<Long> ids);
}
