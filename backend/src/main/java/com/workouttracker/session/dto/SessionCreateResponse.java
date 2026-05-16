package com.workouttracker.session.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 세션 생성 응답 DTO.
 *
 * <p>출처: docs/design.md 3.5 POST /sessions Response
 */
@Schema(description = "세션 생성 응답")
public record SessionCreateResponse(
        @Schema(description = "생성된 세션 ID", example = "123")
        Long sessionId
) {}
