package com.workouttracker.exercise;

import com.workouttracker.common.error.BusinessException;
import com.workouttracker.common.error.ErrorCode;
import com.workouttracker.exercise.dto.ExerciseListResponse;
import com.workouttracker.exercise.dto.ExerciseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ExerciseService {

    /** 입력 검증용 - 시드 데이터의 body_part 값. */
    private static final Set<String> ALLOWED_BODY_PARTS =
            Set.of("CHEST", "BACK", "LEG", "SHOULDER", "ARM", "CORE");

    private final ExerciseRepository exerciseRepository;

    @Transactional(readOnly = true)
    public ExerciseListResponse list(String bodyPart) {
        List<Exercise> exercises;
        if (StringUtils.hasText(bodyPart)) {
            String normalized = bodyPart.toUpperCase();
            if (!ALLOWED_BODY_PARTS.contains(normalized)) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_FAILED,
                        "bodyPart 값이 올바르지 않습니다. 허용: " + ALLOWED_BODY_PARTS);
            }
            exercises = exerciseRepository.findAllByBodyPartAndIsActiveTrueOrderByIdAsc(normalized);
        } else {
            exercises = exerciseRepository.findAllByIsActiveTrueOrderByIdAsc();
        }

        List<ExerciseResponse> content = exercises.stream()
                .map(ExerciseResponse::from)
                .toList();
        return new ExerciseListResponse(content);
    }
}
