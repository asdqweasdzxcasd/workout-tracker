package com.workouttracker.session.dto;

import com.workouttracker.exercise.dto.ExerciseResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 세션 상세 응답 DTO.
 *
 * <p>출처: docs/design.md 3.5 GET /sessions/{id}
 *
 * <p>운동 정보(ExerciseResponse) 까지 포함하므로 FE 에서 별도 조회 불필요.
 */
@Schema(description = "세션 상세 응답")
public record SessionDetailResponse(

        @Schema(description = "세션 ID", example = "123")
        Long id,

        @Schema(description = "수행 날짜", example = "2026-05-16")
        LocalDate performedOn,

        @Schema(description = "메모", example = "가슴/삼두")
        String memo,

        @Schema(description = "생성 시각 (ISO-8601)", example = "2026-05-16T10:30:00+09:00")
        OffsetDateTime createdAt,

        @Schema(description = "운동 목록 (orderNo ASC)")
        List<SessionExerciseDetail> exercises
) {

    /** 세션 내 운동 1개 상세 (운동 정보 + 세트 배열) */
    @Schema(description = "세션 내 운동 상세")
    public record SessionExerciseDetail(

            @Schema(description = "정렬 순서", example = "1")
            Integer orderNo,

            @Schema(description = "운동 종류 정보")
            ExerciseResponse exercise,

            @Schema(description = "세트 목록 (setNo ASC)")
            List<SessionSetDetail> sets
    ) {}

    /** 세트 1개 상세 */
    @Schema(description = "세트 상세")
    public record SessionSetDetail(

            @Schema(description = "세트 번호", example = "1")
            Integer setNo,

            @Schema(description = "무게 (kg)", example = "60.0")
            BigDecimal weightKg,

            @Schema(description = "반복 횟수", example = "10")
            Integer reps
    ) {}
}
