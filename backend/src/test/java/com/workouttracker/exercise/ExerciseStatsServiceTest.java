package com.workouttracker.exercise;

import com.workouttracker.common.error.BusinessException;
import com.workouttracker.common.error.ErrorCode;
import com.workouttracker.exercise.dto.ExerciseStatsResponse;
import com.workouttracker.exercise.dto.PrResult;
import com.workouttracker.exercise.dto.RecentSessionRow;
import com.workouttracker.session.ExerciseSetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * ExerciseStatsService 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExerciseStatsService 단위 테스트")
class ExerciseStatsServiceTest {

    @Mock private ExerciseRepository exerciseRepository;
    @Mock private ExerciseSetRepository exerciseSetRepository;

    @InjectMocks
    private ExerciseStatsService exerciseStatsService;

    private static final Long USER_ID = 1L;
    private static final Long EXERCISE_ID = 10L;

    private Exercise newExercise() {
        Exercise e = Exercise.create(
                "BENCH_PRESS", "벤치프레스", "Bench Press", "CHEST", "COMPOUND", true);
        ReflectionTestUtils.setField(e, "id", EXERCISE_ID);
        return e;
    }

    @Test
    @DisplayName("운동 ID 가 존재하지 않으면 NOT_FOUND")
    void exerciseNotFound() {
        when(exerciseRepository.findById(EXERCISE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exerciseStatsService.getStats(USER_ID, EXERCISE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("기록 없음 - PR 필드는 null, recentSessions 는 빈 배열")
    void noRecords() {
        when(exerciseRepository.findById(EXERCISE_ID)).thenReturn(Optional.of(newExercise()));
        when(exerciseSetRepository.findTopWeight(eq(USER_ID), eq(EXERCISE_ID), any(Pageable.class)))
                .thenReturn(List.of());
        when(exerciseSetRepository.findRecentSessions(eq(USER_ID), eq(EXERCISE_ID), any(Pageable.class)))
                .thenReturn(List.of());

        ExerciseStatsResponse resp = exerciseStatsService.getStats(USER_ID, EXERCISE_ID);

        assertThat(resp.exerciseId()).isEqualTo(EXERCISE_ID);
        assertThat(resp.name()).isEqualTo("벤치프레스");
        assertThat(resp.personalRecordKg()).isNull();
        assertThat(resp.personalRecordDate()).isNull();
        assertThat(resp.recentSessions()).isEmpty();
    }

    @Test
    @DisplayName("정상 - PR + 최근 세션 topSet 매핑")
    void success() {
        when(exerciseRepository.findById(EXERCISE_ID)).thenReturn(Optional.of(newExercise()));
        when(exerciseSetRepository.findTopWeight(eq(USER_ID), eq(EXERCISE_ID), any(Pageable.class)))
                .thenReturn(List.of(new PrResult(new BigDecimal("100.00"), LocalDate.of(2026, 5, 10))));
        when(exerciseSetRepository.findRecentSessions(eq(USER_ID), eq(EXERCISE_ID), any(Pageable.class)))
                .thenReturn(List.of(
                        new RecentSessionRow(123L, LocalDate.of(2026, 5, 16), new BigDecimal("90.00")),
                        new RecentSessionRow(120L, LocalDate.of(2026, 5, 14), new BigDecimal("85.00"))
                ));
        // findSetsForSessions - 세션 ID 별 가장 무거운 세트가 먼저 오도록 정렬
        when(exerciseSetRepository.findSetsForSessions(eq(EXERCISE_ID), any(Collection.class)))
                .thenReturn(List.of(
                        // session 120 의 세트들 (weight DESC)
                        new Object[]{120L, new BigDecimal("85.00"), 6},
                        new Object[]{120L, new BigDecimal("80.00"), 8},
                        // session 123 의 세트들
                        new Object[]{123L, new BigDecimal("90.00"), 5},
                        new Object[]{123L, new BigDecimal("80.00"), 8}
                ));

        ExerciseStatsResponse resp = exerciseStatsService.getStats(USER_ID, EXERCISE_ID);

        assertThat(resp.personalRecordKg()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(resp.personalRecordDate()).isEqualTo(LocalDate.of(2026, 5, 10));
        assertThat(resp.recentSessions()).hasSize(2);

        assertThat(resp.recentSessions().get(0).sessionId()).isEqualTo(123L);
        assertThat(resp.recentSessions().get(0).topSet().weightKg())
                .isEqualByComparingTo(new BigDecimal("90.00"));
        assertThat(resp.recentSessions().get(0).topSet().reps()).isEqualTo(5);

        assertThat(resp.recentSessions().get(1).sessionId()).isEqualTo(120L);
        assertThat(resp.recentSessions().get(1).topSet().weightKg())
                .isEqualByComparingTo(new BigDecimal("85.00"));
        assertThat(resp.recentSessions().get(1).topSet().reps()).isEqualTo(6);
    }
}
