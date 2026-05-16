package com.workouttracker.exercise;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 운동 종류 마스터 엔티티 (시드 데이터).
 *
 * <p>출처: docs/design.md 2.2 exercises DDL
 */
@Entity
@Table(name = "exercises")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Exercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name_ko", nullable = false, length = 50)
    private String nameKo;

    @Column(name = "name_en", nullable = false, length = 80)
    private String nameEn;

    @Column(name = "body_part", nullable = false, length = 20)
    private String bodyPart;

    @Column(name = "category", nullable = false, length = 20)
    private String category;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    /**
     * 테스트/시드 데이터 생성용 정적 팩토리 메서드.
     *
     * <p>운영 환경에서는 Flyway 마이그레이션(V2__seed_exercises.sql)으로 적재되며,
     * 본 메서드는 통합 테스트(H2 + JPA create-drop) 및 단위 테스트에서 사용된다.
     */
    public static Exercise create(
            String code,
            String nameKo,
            String nameEn,
            String bodyPart,
            String category,
            boolean isActive) {
        Exercise e = new Exercise();
        e.code = code;
        e.nameKo = nameKo;
        e.nameEn = nameEn;
        e.bodyPart = bodyPart;
        e.category = category;
        e.isActive = isActive;
        return e;
    }
}
