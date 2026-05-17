package com.workouttracker.session.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 세션 목록 한 행 응답 DTO.
 *
 * <p>출처: docs/design.md 3.5 GET /sessions Response.content[]
 *
 * <p>photoCount 는 Day 3 범위에서는 0 고정 (Day 5 에서 photo 도메인 추가 후 갱신 예정).
 */
@Schema(description = "세션 목록 항목")
public record SessionListItemResponse(

        @Schema(description = "세션 ID", example = "123")
        Long id,

        @Schema(description = "수행 날짜", example = "2026-05-16")
        LocalDate performedOn,

        @Schema(description = "메모", example = "가슴/삼두")
        String memo,

        @Schema(description = "운동 종목 수", example = "3")
        long exerciseCount,

        @Schema(description = "총 세트 수", example = "9")
        long totalSets,

        @Schema(description = "총 볼륨 (무게 x 횟수 합)", example = "4520.0")
        BigDecimal totalVolume,

        @Schema(description = "인증샷 수", example = "1")
        long photoCount
) {
    public static SessionListItemResponse of(SessionListProjection p) {
        return new SessionListItemResponse(
                p.id(),
                p.performedOn(),
                p.memo(),
                p.exerciseCount(),
                p.totalSets(),
                p.totalVolume() != null ? p.totalVolume() : BigDecimal.ZERO,
                p.photoCount()
        );
    }
}
