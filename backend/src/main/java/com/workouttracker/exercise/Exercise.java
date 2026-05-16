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
}
