package com.workouttracker.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 운동 세트(무게/횟수) 엔티티.
 *
 * <p>출처: docs/design.md 2.2 exercise_sets DDL
 *
 * <ul>
 *   <li>weightKg - BigDecimal, NUMERIC(6,2) - 무게 소수점 정밀도 보장</li>
 *   <li>UNIQUE (session_exercise_id, set_no) - JPA 에도 명시</li>
 *   <li>sessionExercise: ManyToOne LAZY</li>
 * </ul>
 */
@Entity
@Table(
        name = "exercise_sets",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_exercise_sets_session_exercise_set_no",
                        columnNames = {"session_exercise_id", "set_no"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExerciseSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_exercise_id", nullable = false)
    private SessionExercise sessionExercise;

    @Column(name = "set_no", nullable = false)
    private Integer setNo;

    /** NUMERIC(6,2) - precision 6, scale 2 */
    @Column(name = "weight_kg", nullable = false, precision = 6, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "reps", nullable = false)
    private Integer reps;

    @Builder
    private ExerciseSet(Integer setNo, BigDecimal weightKg, Integer reps) {
        this.setNo = setNo;
        this.weightKg = weightKg;
        this.reps = reps;
    }

    /** SessionExercise.addSet() 에서만 호출 */
    void assignSessionExercise(SessionExercise sessionExercise) {
        this.sessionExercise = sessionExercise;
    }
}
