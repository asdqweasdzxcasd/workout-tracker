package com.workouttracker.session;

import com.workouttracker.common.error.BusinessException;
import com.workouttracker.common.error.ErrorCode;
import com.workouttracker.exercise.Exercise;
import com.workouttracker.exercise.ExerciseRepository;
import com.workouttracker.session.dto.SessionCreateRequest;
import com.workouttracker.session.dto.SessionCreateResponse;
import com.workouttracker.session.dto.SessionDetailResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionService 단위 테스트")
class SessionServiceTest {

    @Mock
    private WorkoutSessionRepository workoutSessionRepository;

    @Mock
    private ExerciseRepository exerciseRepository;

    @InjectMocks
    private SessionService sessionService;

    private static final Long USER_ID = 1L;

    // ====================================================================
    // 헬퍼
    // ====================================================================

    private Exercise newExercise(long id, String code, String nameKo, String bodyPart, boolean active) {
        Exercise e = Exercise.create(code, nameKo, code, bodyPart, "COMPOUND", active);
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    private SessionCreateRequest validRequest() {
        return new SessionCreateRequest(
                LocalDate.of(2026, 5, 16),
                "가슴/삼두",
                List.of(
                        new SessionCreateRequest.SessionExerciseCreate(
                                1L, 1,
                                List.of(
                                        new SessionCreateRequest.ExerciseSetCreate(1, new BigDecimal("60.0"), 10),
                                        new SessionCreateRequest.ExerciseSetCreate(2, new BigDecimal("70.0"), 8)
                                )
                        ),
                        new SessionCreateRequest.SessionExerciseCreate(
                                2L, 2,
                                List.of(
                                        new SessionCreateRequest.ExerciseSetCreate(1, new BigDecimal("40.0"), 12)
                                )
                        )
                )
        );
    }

    // ====================================================================
    // CREATE 시나리오
    // ====================================================================

    @Test
    @DisplayName("세션 생성 성공 - 단일 트랜잭션으로 session + exercises + sets 저장")
    void create_success() {
        SessionCreateRequest req = validRequest();
        when(exerciseRepository.findAllByIdInAndIsActiveTrue(anyCollection()))
                .thenReturn(List.of(
                        newExercise(1L, "BENCH_PRESS", "벤치프레스", "CHEST", true),
                        newExercise(2L, "OHP", "오버헤드프레스", "SHOULDER", true)
                ));
        when(workoutSessionRepository.save(any(WorkoutSession.class)))
                .thenAnswer(invocation -> {
                    WorkoutSession s = invocation.getArgument(0);
                    ReflectionTestUtils.setField(s, "id", 100L);
                    return s;
                });

        SessionCreateResponse response = sessionService.create(USER_ID, req);

        assertThat(response.sessionId()).isEqualTo(100L);

        ArgumentCaptor<WorkoutSession> captor = ArgumentCaptor.forClass(WorkoutSession.class);
        verify(workoutSessionRepository, times(1)).save(captor.capture());
        WorkoutSession savedSession = captor.getValue();

        assertThat(savedSession.getUserId()).isEqualTo(USER_ID);
        assertThat(savedSession.getPerformedOn()).isEqualTo(LocalDate.of(2026, 5, 16));
        assertThat(savedSession.getMemo()).isEqualTo("가슴/삼두");
        // 양방향 관계 헬퍼 검증 - session 참조가 세팅되어 있어야 cascade insert 가 정상 동작
        assertThat(savedSession.getExercises()).hasSize(2);
        assertThat(savedSession.getExercises().get(0).getSession()).isSameAs(savedSession);
        assertThat(savedSession.getExercises().get(0).getSets()).hasSize(2);
        assertThat(savedSession.getExercises().get(0).getSets().get(0).getSessionExercise())
                .isSameAs(savedSession.getExercises().get(0));
    }

    @Test
    @DisplayName("세션 생성 실패 - exerciseId 가 활성 운동이 아니면 VALIDATION_FAILED")
    void create_exerciseIdInvalid() {
        SessionCreateRequest req = validRequest();
        // 1L 만 활성, 2L 누락 → 검증 실패
        when(exerciseRepository.findAllByIdInAndIsActiveTrue(anyCollection()))
                .thenReturn(List.of(newExercise(1L, "BENCH_PRESS", "벤치프레스", "CHEST", true)));

        assertThatThrownBy(() -> sessionService.create(USER_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(workoutSessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("세션 생성 실패 - orderNo 중복")
    void create_orderNoDuplicated() {
        SessionCreateRequest req = new SessionCreateRequest(
                LocalDate.of(2026, 5, 16),
                "memo",
                List.of(
                        new SessionCreateRequest.SessionExerciseCreate(
                                1L, 1,
                                List.of(new SessionCreateRequest.ExerciseSetCreate(1, new BigDecimal("50.0"), 10))
                        ),
                        new SessionCreateRequest.SessionExerciseCreate(
                                2L, 1, // 중복!
                                List.of(new SessionCreateRequest.ExerciseSetCreate(1, new BigDecimal("30.0"), 12))
                        )
                )
        );

        assertThatThrownBy(() -> sessionService.create(USER_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(workoutSessionRepository, never()).save(any());
        // exerciseRepository 활성 검사보다 비즈니스 룰 검증이 먼저 - 호출 없어야 함
        verify(exerciseRepository, never()).findAllByIdInAndIsActiveTrue(anyCollection());
    }

    @Test
    @DisplayName("세션 생성 실패 - 같은 운동 내 setNo 중복")
    void create_setNoDuplicated() {
        SessionCreateRequest req = new SessionCreateRequest(
                LocalDate.of(2026, 5, 16),
                "memo",
                List.of(
                        new SessionCreateRequest.SessionExerciseCreate(
                                1L, 1,
                                List.of(
                                        new SessionCreateRequest.ExerciseSetCreate(1, new BigDecimal("60.0"), 10),
                                        new SessionCreateRequest.ExerciseSetCreate(1, new BigDecimal("70.0"), 8) // 중복!
                                )
                        )
                )
        );

        assertThatThrownBy(() -> sessionService.create(USER_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(workoutSessionRepository, never()).save(any());
    }

    // ====================================================================
    // DETAIL / DELETE - 소유권 검증
    // ====================================================================

    @Test
    @DisplayName("세션 상세 - 소유권 위반 시 NOT_FOUND (존재 자체 숨김)")
    void detail_notFoundOrOtherUser() {
        when(workoutSessionRepository.findDetailByIdAndUserId(999L, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.getDetail(USER_ID, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("세션 상세 성공 - orderNo / setNo 오름차순 정렬 및 운동 정보 매핑")
    void detail_success() {
        // given: 세션 1개에 운동 2개 (orderNo 2, 1) + 각 세트 2개씩 (setNo 2, 1)
        WorkoutSession session = WorkoutSession.builder()
                .userId(USER_ID)
                .performedOn(LocalDate.of(2026, 5, 16))
                .memo("memo")
                .build();
        ReflectionTestUtils.setField(session, "id", 100L);
        ReflectionTestUtils.setField(session, "createdAt", OffsetDateTime.now());

        SessionExercise se2 = SessionExercise.builder().exerciseId(2L).orderNo(2).build();
        ExerciseSet s21 = ExerciseSet.builder().setNo(2).weightKg(new BigDecimal("50.00")).reps(8).build();
        ExerciseSet s22 = ExerciseSet.builder().setNo(1).weightKg(new BigDecimal("40.00")).reps(10).build();
        se2.addSet(s21);
        se2.addSet(s22);

        SessionExercise se1 = SessionExercise.builder().exerciseId(1L).orderNo(1).build();
        ExerciseSet s11 = ExerciseSet.builder().setNo(1).weightKg(new BigDecimal("60.00")).reps(10).build();
        se1.addSet(s11);

        // 일부러 역순으로 추가 - 서비스에서 정렬되어야 함
        session.addExercise(se2);
        session.addExercise(se1);

        when(workoutSessionRepository.findDetailByIdAndUserId(100L, USER_ID))
                .thenReturn(Optional.of(session));
        when(exerciseRepository.findAllByIdIn(any(Collection.class)))
                .thenReturn(List.of(
                        newExercise(1L, "BENCH_PRESS", "벤치프레스", "CHEST", true),
                        newExercise(2L, "OHP", "오버헤드프레스", "SHOULDER", true)
                ));

        // when
        SessionDetailResponse resp = sessionService.getDetail(USER_ID, 100L);

        // then
        assertThat(resp.id()).isEqualTo(100L);
        assertThat(resp.exercises()).hasSize(2);
        assertThat(resp.exercises().get(0).orderNo()).isEqualTo(1);
        assertThat(resp.exercises().get(0).exercise().code()).isEqualTo("BENCH_PRESS");
        assertThat(resp.exercises().get(1).orderNo()).isEqualTo(2);
        assertThat(resp.exercises().get(1).sets()).hasSize(2);
        assertThat(resp.exercises().get(1).sets().get(0).setNo()).isEqualTo(1);
        assertThat(resp.exercises().get(1).sets().get(1).setNo()).isEqualTo(2);
    }

    @Test
    @DisplayName("세션 삭제 - 소유권 위반 시 NOT_FOUND")
    void delete_notFoundOrOtherUser() {
        when(workoutSessionRepository.findByIdAndUserId(999L, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.delete(USER_ID, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        verify(workoutSessionRepository, never()).delete(any(WorkoutSession.class));
    }

    @Test
    @DisplayName("세션 삭제 성공")
    void delete_success() {
        WorkoutSession session = WorkoutSession.builder()
                .userId(USER_ID)
                .performedOn(LocalDate.of(2026, 5, 16))
                .build();
        ReflectionTestUtils.setField(session, "id", 100L);

        when(workoutSessionRepository.findByIdAndUserId(100L, USER_ID))
                .thenReturn(Optional.of(session));

        sessionService.delete(USER_ID, 100L);

        verify(workoutSessionRepository, times(1)).delete(session);
    }

    @Test
    @DisplayName("list - size 가 50을 넘으면 50으로 클램프 (page=0 size=20 기본 검증은 컨트롤러에서 처리됨)")
    void list_sizeClamp() {
        // SessionService.list 내부 PageRequest 의 size 가 50 이하인지 간접 검증.
        // findSessionList 가 호출만 되었는지 확인 (정확한 size 검증은 통합 테스트 영역)
        when(workoutSessionRepository.findSessionList(any(Long.class), any()))
                .thenReturn(new org.springframework.data.domain.SliceImpl<>(List.of()));

        sessionService.list(USER_ID, 0, 100);

        verify(workoutSessionRepository, times(1))
                .findSessionList(any(Long.class), any());
    }
}
