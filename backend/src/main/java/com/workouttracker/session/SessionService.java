package com.workouttracker.session;

import com.workouttracker.common.error.BusinessException;
import com.workouttracker.common.error.ErrorCode;
import com.workouttracker.exercise.Exercise;
import com.workouttracker.exercise.ExerciseRepository;
import com.workouttracker.exercise.dto.ExerciseResponse;
import com.workouttracker.session.dto.SessionCreateRequest;
import com.workouttracker.session.dto.SessionCreateResponse;
import com.workouttracker.session.dto.SessionDetailResponse;
import com.workouttracker.session.dto.SessionListItemResponse;
import com.workouttracker.session.dto.SessionListProjection;
import com.workouttracker.session.dto.SessionPageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 세션 도메인 서비스.
 *
 * <p>설계 문서 참조:
 * <ul>
 *   <li>2.4 트랜잭션 경계 - 세션 생성은 session/session_exercises/exercise_sets 단일 트랜잭션</li>
 *   <li>3.5 POST /sessions 구조</li>
 *   <li>3.6 페이징 규약 - 코드에서 정렬 강제, size 최대 50</li>
 *   <li>7.1 소유권 위반 시 404 (존재 자체 숨김)</li>
 * </ul>
 *
 * <p>동시성: 1인 사용자 도메인이므로 락 없음.
 * 향후 친구/그룹 기능 추가 시 그룹 멤버십에 비관적 락 도입 고려.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    /** 페이징 size 상한 (설계 3.6) */
    public static final int MAX_PAGE_SIZE = 50;
    public static final int DEFAULT_PAGE_SIZE = 20;

    private final WorkoutSessionRepository workoutSessionRepository;
    private final ExerciseRepository exerciseRepository;

    // ====================================================================
    // CREATE - 단일 트랜잭션 핵심
    // ====================================================================

    /**
     * 세션 + 세션 운동 + 세트 일괄 생성.
     *
     * <p>모든 INSERT 는 단일 트랜잭션. 예외 시 전체 롤백.
     *
     * @param userId  인증된 사용자 ID (JWT 에서 추출)
     * @param request 검증된 요청
     * @return 생성된 세션 ID
     */
    @Transactional
    public SessionCreateResponse create(Long userId, SessionCreateRequest request) {
        validateBusinessRules(request);
        validateExercisesActive(request);

        WorkoutSession session = WorkoutSession.builder()
                .userId(userId)
                .performedOn(request.performedOn())
                .memo(request.memo())
                .build();

        for (var exerciseInput : request.exercises()) {
            SessionExercise sessionExercise = SessionExercise.builder()
                    .exerciseId(exerciseInput.exerciseId())
                    .orderNo(exerciseInput.orderNo())
                    .build();

            for (var setInput : exerciseInput.sets()) {
                ExerciseSet set = ExerciseSet.builder()
                        .setNo(setInput.setNo())
                        .weightKg(setInput.weightKg())
                        .reps(setInput.reps())
                        .build();
                sessionExercise.addSet(set);
            }
            session.addExercise(sessionExercise);
        }

        WorkoutSession saved = workoutSessionRepository.save(session);
        log.info("세션 생성: userId={} sessionId={} exerciseCount={}",
                userId, saved.getId(), saved.getExercises().size());
        return new SessionCreateResponse(saved.getId());
    }

    /**
     * Bean Validation 으로 잡지 못하는 비즈니스 룰 검증.
     * <ul>
     *   <li>orderNo 중복</li>
     *   <li>setNo 중복 (운동 내 단위)</li>
     * </ul>
     */
    private void validateBusinessRules(SessionCreateRequest request) {
        Set<Integer> orderNos = new HashSet<>();
        for (var exercise : request.exercises()) {
            if (!orderNos.add(exercise.orderNo())) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_FAILED,
                        "exercises 의 orderNo 가 중복됩니다: " + exercise.orderNo());
            }

            Set<Integer> setNos = new HashSet<>();
            for (var set : exercise.sets()) {
                if (!setNos.add(set.setNo())) {
                    throw new BusinessException(
                            ErrorCode.VALIDATION_FAILED,
                            "exerciseId=" + exercise.exerciseId()
                                    + " 의 sets 에서 setNo 가 중복됩니다: " + set.setNo());
                }
            }
        }
    }

    /**
     * exerciseId 들이 모두 존재하고 활성(is_active=true)인지 확인.
     * 부분 누락 시 어떤 ID 가 문제인지 메시지에 포함해 디버깅 편의 제공.
     */
    private void validateExercisesActive(SessionCreateRequest request) {
        Set<Long> requestedIds = request.exercises().stream()
                .map(SessionCreateRequest.SessionExerciseCreate::exerciseId)
                .collect(Collectors.toSet());

        List<Exercise> activeExercises =
                exerciseRepository.findAllByIdInAndIsActiveTrue(requestedIds);

        if (activeExercises.size() != requestedIds.size()) {
            Set<Long> foundIds = activeExercises.stream()
                    .map(Exercise::getId)
                    .collect(Collectors.toSet());
            Set<Long> missing = new HashSet<>(requestedIds);
            missing.removeAll(foundIds);
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "존재하지 않거나 비활성 상태인 exerciseId 가 있습니다: " + missing);
        }
    }

    // ====================================================================
    // LIST - 단일 쿼리 페이징 + 집계
    // ====================================================================

    /**
     * 내 세션 목록 (페이징 + 집계 단일 쿼리).
     *
     * <p>page < 0 → 0, size <= 0 → 기본값, size > MAX → 클램프.
     */
    @Transactional(readOnly = true)
    public SessionPageResponse list(Long userId, Integer page, Integer size) {
        int safePage = (page == null || page < 0) ? 0 : page;
        int safeSize = (size == null || size <= 0) ? DEFAULT_PAGE_SIZE
                : Math.min(size, MAX_PAGE_SIZE);

        // 정렬은 JPQL ORDER BY 로 강제하므로 Pageable.unsorted() 사용
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Slice<SessionListProjection> projections =
                workoutSessionRepository.findSessionList(userId, pageable);

        Slice<SessionListItemResponse> items =
                projections.map(SessionListItemResponse::of);
        return SessionPageResponse.from(items);
    }

    // ====================================================================
    // DETAIL - fetch join (N+1 회피)
    // ====================================================================

    /**
     * 세션 상세 조회.
     *
     * <ul>
     *   <li>소유권 위반 (없거나 남의 세션) → NOT_FOUND</li>
     *   <li>exercises, sets 모두 fetch join 으로 1 쿼리 + 운동 정보 일괄 조회 1 쿼리</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public SessionDetailResponse getDetail(Long userId, Long sessionId) {
        WorkoutSession session = workoutSessionRepository
                .findDetailByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        // 운동 정보 일괄 조회 (N+1 회피)
        Set<Long> exerciseIds = session.getExercises().stream()
                .map(SessionExercise::getExerciseId)
                .collect(Collectors.toSet());
        Map<Long, Exercise> exerciseMap = exerciseRepository.findAllByIdIn(exerciseIds).stream()
                .collect(Collectors.toMap(Exercise::getId, Function.identity()));

        List<SessionDetailResponse.SessionExerciseDetail> exerciseDetails =
                session.getExercises().stream()
                        .sorted(java.util.Comparator.comparing(SessionExercise::getOrderNo))
                        .map(se -> {
                            Exercise ex = exerciseMap.get(se.getExerciseId());
                            ExerciseResponse exerciseDto = (ex != null)
                                    ? ExerciseResponse.from(ex)
                                    : null;
                            List<SessionDetailResponse.SessionSetDetail> setDetails =
                                    se.getSets().stream()
                                            .sorted(java.util.Comparator.comparing(ExerciseSet::getSetNo))
                                            .map(s -> new SessionDetailResponse.SessionSetDetail(
                                                    s.getSetNo(), s.getWeightKg(), s.getReps()))
                                            .toList();
                            return new SessionDetailResponse.SessionExerciseDetail(
                                    se.getOrderNo(), exerciseDto, setDetails);
                        })
                        .toList();

        return new SessionDetailResponse(
                session.getId(),
                session.getPerformedOn(),
                session.getMemo(),
                session.getCreatedAt(),
                exerciseDetails);
    }

    // ====================================================================
    // DELETE
    // ====================================================================

    /**
     * 세션 삭제 (CASCADE 로 하위 row 자동 삭제).
     * 소유권 위반 시 NOT_FOUND.
     */
    @Transactional
    public void delete(Long userId, Long sessionId) {
        WorkoutSession session = workoutSessionRepository
                .findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        workoutSessionRepository.delete(session);
        log.info("세션 삭제: userId={} sessionId={}", userId, sessionId);
    }
}
