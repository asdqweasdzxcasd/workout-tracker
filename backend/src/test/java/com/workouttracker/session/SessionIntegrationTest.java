package com.workouttracker.session;

import com.workouttracker.exercise.Exercise;
import com.workouttracker.exercise.ExerciseRepository;
import com.workouttracker.session.dto.SessionCreateRequest;
import com.workouttracker.session.dto.SessionCreateResponse;
import com.workouttracker.session.dto.SessionDetailResponse;
import com.workouttracker.session.dto.SessionPageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 세션 도메인 통합 테스트.
 *
 * <p>H2 in-memory + JPA create-drop (test 프로필). 실제 DB 호출까지 검증.
 *
 * <p>커버 시나리오:
 * <ul>
 *   <li>세션 생성 → 목록 조회 → 상세 조회 → 삭제 → 다시 조회 시 404</li>
 *   <li>소유권 위반 (다른 사용자 ID) → NOT_FOUND</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Session 통합 테스트")
class SessionIntegrationTest {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private ExerciseRepository exerciseRepository;

    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;

    private Long exerciseId1;
    private Long exerciseId2;

    @BeforeEach
    void setUpExercises() {
        // 시드 데이터를 직접 INSERT (H2 + JPA create-drop 환경이므로 V2 마이그레이션 미적용)
        exerciseId1 = exerciseRepository.save(
                Exercise.create("BENCH_PRESS", "벤치프레스", "Bench Press", "CHEST", "COMPOUND", true)
        ).getId();

        exerciseId2 = exerciseRepository.save(
                Exercise.create("OHP", "오버헤드프레스", "Overhead Press", "SHOULDER", "COMPOUND", true)
        ).getId();
    }

    @Test
    @DisplayName("세션 생성 → 목록 조회 → 상세 조회 → 삭제 후 NOT_FOUND")
    void fullLifecycle() {
        // when: 생성
        SessionCreateRequest req = new SessionCreateRequest(
                LocalDate.of(2026, 5, 16),
                "가슴/어깨",
                List.of(
                        new SessionCreateRequest.SessionExerciseCreate(
                                exerciseId1, 1,
                                List.of(
                                        new SessionCreateRequest.ExerciseSetCreate(1, new BigDecimal("60.00"), 10),
                                        new SessionCreateRequest.ExerciseSetCreate(2, new BigDecimal("70.00"), 8)
                                )),
                        new SessionCreateRequest.SessionExerciseCreate(
                                exerciseId2, 2,
                                List.of(
                                        new SessionCreateRequest.ExerciseSetCreate(1, new BigDecimal("40.00"), 12)
                                ))
                )
        );
        SessionCreateResponse created = sessionService.create(USER_A, req);
        assertThat(created.sessionId()).isNotNull();

        // then: 목록 조회 - 1건, 집계값 검증
        SessionPageResponse page = sessionService.list(USER_A, 0, 20);
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).exerciseCount()).isEqualTo(2);
        assertThat(page.content().get(0).totalSets()).isEqualTo(3);
        // totalVolume = 60*10 + 70*8 + 40*12 = 600 + 560 + 480 = 1640
        assertThat(page.content().get(0).totalVolume())
                .isEqualByComparingTo(new BigDecimal("1640.00"));
        assertThat(page.hasNext()).isFalse();

        // then: 상세 조회 - 운동 2개, 정렬 ASC
        SessionDetailResponse detail = sessionService.getDetail(USER_A, created.sessionId());
        assertThat(detail.exercises()).hasSize(2);
        assertThat(detail.exercises().get(0).orderNo()).isEqualTo(1);
        assertThat(detail.exercises().get(0).exercise().code()).isEqualTo("BENCH_PRESS");
        assertThat(detail.exercises().get(0).sets()).hasSize(2);
        assertThat(detail.exercises().get(1).orderNo()).isEqualTo(2);

        // when: 삭제
        sessionService.delete(USER_A, created.sessionId());

        // then: 다시 조회 시 NOT_FOUND
        assertThatThrownBy(() -> sessionService.getDetail(USER_A, created.sessionId()))
                .isInstanceOf(com.workouttracker.common.error.BusinessException.class)
                .extracting(ex -> ((com.workouttracker.common.error.BusinessException) ex).getErrorCode())
                .isEqualTo(com.workouttracker.common.error.ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("다른 사용자의 세션 ID 로 상세 조회 시 NOT_FOUND")
    void detail_ownershipViolation() {
        SessionCreateRequest req = new SessionCreateRequest(
                LocalDate.of(2026, 5, 16),
                null,
                List.of(
                        new SessionCreateRequest.SessionExerciseCreate(
                                exerciseId1, 1,
                                List.of(new SessionCreateRequest.ExerciseSetCreate(
                                        1, new BigDecimal("50.00"), 10))
                        )
                )
        );
        SessionCreateResponse created = sessionService.create(USER_A, req);

        // USER_B 가 USER_A 의 세션을 조회 → NOT_FOUND
        assertThatThrownBy(() -> sessionService.getDetail(USER_B, created.sessionId()))
                .isInstanceOf(com.workouttracker.common.error.BusinessException.class);
    }
}
