package com.workouttracker.session;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 운동 세션 엔티티.
 *
 * <p>출처: docs/design.md 2.2 workout_sessions DDL
 *
 * <p>설계 결정 사항
 * <ul>
 *   <li>performedOn 은 LocalDate (DDL DATE 매핑)</li>
 *   <li>동일 날짜 다중 세션 허용 - unique 제약 없음 (사용자 결정사항)</li>
 *   <li>userId 는 FK 컬럼만 유지 (User 엔티티와의 ManyToOne 매핑은 사용 X)
 *       - 도메인 경계 단순화. 추후 친구/그룹 기능 추가 시 재검토</li>
 *   <li>exercises - cascade=ALL, orphanRemoval=true 로 단일 트랜잭션에서 일괄 저장/삭제</li>
 * </ul>
 *
 * <p>동시성: 1인 사용자 도메인이므로 락 불필요.
 * 추후 친구/그룹 공유 기능 추가 시 그룹 멤버십 검사에 비관적 락 고려.
 */
@Entity
@Table(name = "workout_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkoutSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "performed_on", nullable = false)
    private LocalDate performedOn;

    @Column(name = "memo", length = 500)
    private String memo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * 세션 소속 운동(SessionExercise) 목록.
     * cascade=ALL, orphanRemoval=true 로 트랜잭션 내 일괄 저장/삭제 보장.
     */
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderNo ASC")
    private final List<SessionExercise> exercises = new ArrayList<>();

    @Builder
    private WorkoutSession(Long userId, LocalDate performedOn, String memo) {
        this.userId = userId;
        this.performedOn = performedOn;
        this.memo = memo;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    /**
     * 양방향 관계 안전 설정용 헬퍼.
     * SessionExercise.session 도 함께 채워서 cascade 저장 시 FK가 정확히 세팅되도록 한다.
     */
    public void addExercise(SessionExercise exercise) {
        this.exercises.add(exercise);
        exercise.assignSession(this);
    }

    /** 방어적 노출 - 외부에서 직접 수정 못 하도록 unmodifiable */
    public List<SessionExercise> getExercises() {
        return Collections.unmodifiableList(this.exercises);
    }
}
