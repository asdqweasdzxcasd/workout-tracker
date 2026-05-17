package com.workouttracker.exercise;

import com.workouttracker.common.error.BusinessException;
import com.workouttracker.common.error.ErrorCode;
import com.workouttracker.exercise.dto.ExerciseStatsResponse;
import com.workouttracker.exercise.dto.PrResult;
import com.workouttracker.exercise.dto.RecentSession;
import com.workouttracker.exercise.dto.RecentSessionRow;
import com.workouttracker.exercise.dto.TopSet;
import com.workouttracker.session.ExerciseSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 운동별 PR / 통계 서비스.
 *
 * <p>출처: docs/design.md 3.5 GET /exercises/{id}/stats
 *
 * <p>쿼리 전략 (인덱스 활용):
 * <ul>
 *   <li>PR: ExerciseSet JOIN SessionExercise JOIN WorkoutSession ORDER BY weightKg DESC LIMIT 1</li>
 *   <li>최근 세션: 세션별 GROUP BY MAX(weightKg), 최근 5개</li>
 *   <li>각 세션의 topSet 의 reps: 별도 일괄 조회 후 Java 로 매핑 (JPQL LIMIT 서브쿼리 회피)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ExerciseStatsService {

    private static final int RECENT_SESSION_LIMIT = 5;

    private final ExerciseRepository exerciseRepository;
    private final ExerciseSetRepository exerciseSetRepository;

    @Transactional(readOnly = true)
    public ExerciseStatsResponse getStats(Long userId, Long exerciseId) {
        Exercise exercise = exerciseRepository.findById(exerciseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        // 1) PR - 무게 DESC LIMIT 1
        List<PrResult> prRows =
                exerciseSetRepository.findTopWeight(userId, exerciseId, PageRequest.of(0, 1));
        BigDecimal prKg = prRows.isEmpty() ? null : prRows.get(0).weightKg();
        var prDate = prRows.isEmpty() ? null : prRows.get(0).performedOn();

        // 2) 최근 세션 (sessionId, performedOn, topWeightKg)
        List<RecentSessionRow> recentRows = exerciseSetRepository.findRecentSessions(
                userId, exerciseId, PageRequest.of(0, RECENT_SESSION_LIMIT));

        // 3) reps 매핑 - 한 번에 일괄 조회 후 sessionId 별 첫(=가장 무거운) 행에서 reps 추출
        Map<Long, Integer> topRepsBySession = resolveTopReps(exerciseId, recentRows);

        List<RecentSession> recent = recentRows.stream()
                .map(row -> {
                    Integer reps = topRepsBySession.get(row.sessionId());
                    TopSet topSet = (row.topWeightKg() != null)
                            ? new TopSet(row.topWeightKg(), reps)
                            : null;
                    return new RecentSession(row.sessionId(), row.performedOn(), topSet);
                })
                .toList();

        return new ExerciseStatsResponse(
                exercise.getId(),
                exercise.getNameKo(),
                prKg,
                prDate,
                recent
        );
    }

    /**
     * 세션 ID → 최고 무게의 reps 매핑.
     *
     * <p>{@link ExerciseSetRepository#findSetsForSessions} 로 (sessionId, weightKg, reps) 들을
     * weightKg DESC 정렬로 가져오고, 세션마다 첫 등장 행만 채택한다.
     */
    private Map<Long, Integer> resolveTopReps(Long exerciseId, List<RecentSessionRow> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<Long> sessionIds = rows.stream().map(RecentSessionRow::sessionId).toList();
        List<Object[]> raws = exerciseSetRepository.findSetsForSessions(exerciseId, sessionIds);

        Map<Long, Integer> result = new HashMap<>();
        for (Object[] row : raws) {
            Long sessionId = (Long) row[0];
            Integer reps = (Integer) row[2];
            // 무게 DESC 로 정렬되어 있으므로 첫 등장만 채택 (가장 무거운 세트의 reps)
            result.putIfAbsent(sessionId, reps);
        }
        return result;
    }
}
