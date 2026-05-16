package com.workouttracker.session.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Slice;

import java.util.List;

/**
 * 페이징 응답 래퍼.
 *
 * <p>출처: docs/design.md 3.1 페이징 규약, 3.5 GET /sessions
 *
 * <p>Spring {@link Slice} 기반 - 정확한 totalElements 가 필요 없는 무한 스크롤/Next 패턴에 적합.
 * 단, 본 MVP 는 단순 페이지 번호 UI 이므로 size 만 큰 의미를 갖고 totalElements 는
 * Slice 의 content 크기로 노출한다 (실제 전체 개수가 아닌 현재 페이지 항목 수).
 */
@Schema(description = "세션 페이지 응답")
public record SessionPageResponse(

        @Schema(description = "세션 목록")
        List<SessionListItemResponse> content,

        @Schema(description = "현재 페이지 번호 (0부터)", example = "0")
        int page,

        @Schema(description = "페이지 크기", example = "20")
        int size,

        @Schema(description = "현재 페이지에 포함된 항목 수 (Slice 기반)", example = "1")
        long totalElements,

        @Schema(description = "다음 페이지 존재 여부", example = "false")
        boolean hasNext
) {
    public static SessionPageResponse from(Slice<SessionListItemResponse> slice) {
        return new SessionPageResponse(
                slice.getContent(),
                slice.getNumber(),
                slice.getSize(),
                slice.getNumberOfElements(),
                slice.hasNext()
        );
    }
}
