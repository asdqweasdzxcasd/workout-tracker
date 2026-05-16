package com.workouttracker.exercise;

import com.workouttracker.common.error.BusinessException;
import com.workouttracker.common.error.ErrorCode;
import com.workouttracker.exercise.dto.ExerciseListResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExerciseService 단위 테스트")
class ExerciseServiceTest {

    @Mock
    private ExerciseRepository exerciseRepository;

    @InjectMocks
    private ExerciseService exerciseService;

    private Exercise newExercise(long id, String code, String nameKo, String bodyPart) {
        Exercise e = new Exercise();
        ReflectionTestUtils.setField(e, "id", id);
        ReflectionTestUtils.setField(e, "code", code);
        ReflectionTestUtils.setField(e, "nameKo", nameKo);
        ReflectionTestUtils.setField(e, "nameEn", code);
        ReflectionTestUtils.setField(e, "bodyPart", bodyPart);
        ReflectionTestUtils.setField(e, "category", "COMPOUND");
        ReflectionTestUtils.setField(e, "isActive", true);
        return e;
    }

    @Test
    @DisplayName("필터 없음 - 전체 활성 운동 조회")
    void list_all() {
        // given
        when(exerciseRepository.findAllByIsActiveTrueOrderByIdAsc())
                .thenReturn(List.of(
                        newExercise(1L, "BENCH_PRESS", "벤치프레스", "CHEST"),
                        newExercise(2L, "SQUAT", "스쿼트", "LEG")
                ));

        // when
        ExerciseListResponse response = exerciseService.list(null);

        // then
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).code()).isEqualTo("BENCH_PRESS");
        verify(exerciseRepository, times(1)).findAllByIsActiveTrueOrderByIdAsc();
        verify(exerciseRepository, never())
                .findAllByBodyPartAndIsActiveTrueOrderByIdAsc(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("bodyPart 필터 - 정상 값은 소문자 입력도 정규화하여 조회")
    void list_byBodyPart_normalized() {
        when(exerciseRepository.findAllByBodyPartAndIsActiveTrueOrderByIdAsc("CHEST"))
                .thenReturn(List.of(newExercise(1L, "BENCH_PRESS", "벤치프레스", "CHEST")));

        ExerciseListResponse response = exerciseService.list("chest");

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).bodyPart()).isEqualTo("CHEST");
        verify(exerciseRepository, times(1))
                .findAllByBodyPartAndIsActiveTrueOrderByIdAsc("CHEST");
    }

    @Test
    @DisplayName("bodyPart 필터 - 허용 외 값은 VALIDATION_FAILED")
    void list_byBodyPart_invalid() {
        assertThatThrownBy(() -> exerciseService.list("INVALID_PART"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(exerciseRepository, never())
                .findAllByBodyPartAndIsActiveTrueOrderByIdAsc(org.mockito.ArgumentMatchers.anyString());
    }
}
