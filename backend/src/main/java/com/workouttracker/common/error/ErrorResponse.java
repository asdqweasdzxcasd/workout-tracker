package com.workouttracker.common.error;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 표준 에러 응답 DTO.
 *
 * <p>출처: docs/design.md 3.2 표준 에러 응답
 */
@Schema(description = "표준 에러 응답")
public record ErrorResponse(
        @Schema(description = "에러 발생 시각(ISO-8601)", example = "2026-05-16T10:30:00Z")
        OffsetDateTime timestamp,

        @Schema(description = "HTTP 상태 코드", example = "400")
        int status,

        @Schema(description = "에러 코드", example = "VALIDATION_FAILED")
        String code,

        @Schema(description = "사용자에게 노출할 메시지", example = "요청 값 검증에 실패했습니다.")
        String message,

        @Schema(description = "요청 경로", example = "/api/v1/auth/signup")
        String path,

        @Schema(description = "추적용 traceId(UUID)", example = "abc123")
        String traceId
) {
    public static ErrorResponse of(ErrorCode errorCode, String message, String path, String traceId) {
        return new ErrorResponse(
                OffsetDateTime.now(),
                errorCode.getStatus().value(),
                errorCode.name(),
                message,
                path,
                traceId
        );
    }
}
