package com.workouttracker.photo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 세션 인증샷 메타데이터 리포지토리.
 *
 * <p>설계: docs/design.md 2.2 session_photos, 3.4 photo 엔드포인트
 */
public interface SessionPhotoRepository extends JpaRepository<SessionPhoto, Long> {

    /**
     * 세션 소속 사진 목록 (최근 업로드 순).
     * 호출 전 세션 소유권은 별도 검증한다.
     */
    List<SessionPhoto> findAllBySessionIdAndUserIdOrderByUploadedAtDesc(
            Long sessionId, Long userId);

    /**
     * 소유권 검증용 - 본인 사진만 조회.
     * 다른 사용자의 photoId 가 와도 빈 Optional → 컨트롤러가 404 변환.
     */
    Optional<SessionPhoto> findByIdAndUserId(Long id, Long userId);

    /**
     * 세션별 사진 수 단건 카운트.
     * <p>주의: 목록 페이징 집계에서는 WorkoutSessionRepository 의 JPQL 서브쿼리를 사용하고,
     * 본 메서드는 단건 검증/디버깅 용도.
     */
    long countBySessionId(Long sessionId);
}
