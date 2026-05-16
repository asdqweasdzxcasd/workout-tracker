package com.workouttracker.session;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 세션 내 운동(=한 운동 종목의 회차) 엔티티.
 *
 * <p>출처: docs/design.md 2.2 session_exercises DDL
 *
 * <ul>
 *   <li>UNIQUE (session_id, order_no) 제약 - DDL과 일치하도록 JPA 에서도 명시</li>
 *   <li>session: ManyToOne LAZY (목록/페이징 시 N+1 회피)</li>
 *   <li>exerciseId 는 FK 컬럼만 유지 - 상세 조회 시 별도 일괄 조회로 매핑</li>
 *   <li>sets - cascade=ALL, orphanRemoval=true</li>
 * </ul>
 */
@Entity
@Table(
        name = "session_exercises",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_session_exercises_session_order",
                        columnNames = {"session_id", "order_no"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionExercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 부모 세션 (양방향).
     * insertable/updatable=false 로 두지 않고 일반 매핑.
     * 헬퍼 메서드 addExercise() 를 통해서만 세팅되도록 권장.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private WorkoutSession session;

    @Column(name = "exercise_id", nullable = false)
    private Long exerciseId;

    @Column(name = "order_no", nullable = false)
    private Integer orderNo;

    /**
     * 세션 내 세트 목록. fetch=LAZY + @BatchSize 로 상세 조회 시 N+1 회피.
     *
     * <p>WorkoutSessionRepository.findDetailByIdAndUserId 가 exercises 만 fetch join 하므로,
     * 다음 단계 sets 접근 시 BatchSize 만큼 IN 절로 한 번에 로딩 (MultipleBagFetchException 회피).
     */
    @OneToMany(mappedBy = "sessionExercise", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("setNo ASC")
    @BatchSize(size = 50)
    private final List<ExerciseSet> sets = new ArrayList<>();

    @Builder
    private SessionExercise(Long exerciseId, Integer orderNo) {
        this.exerciseId = exerciseId;
        this.orderNo = orderNo;
    }

    /** WorkoutSession.addExercise() 에서만 호출 - 양방향 관계 안전 보장 */
    void assignSession(WorkoutSession session) {
        this.session = session;
    }

    /** 세트 추가용 헬퍼 - 양방향 관계 안전 설정 */
    public void addSet(ExerciseSet set) {
        this.sets.add(set);
        set.assignSessionExercise(this);
    }

    public List<ExerciseSet> getSets() {
        return Collections.unmodifiableList(this.sets);
    }
}
