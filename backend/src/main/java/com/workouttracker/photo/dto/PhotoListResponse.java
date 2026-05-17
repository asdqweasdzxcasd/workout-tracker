package com.workouttracker.photo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 세션 사진 목록 응답.
 *
 * <p>1 세션 N 사진. 다운로드 presigned URL 은 각 항목에 포함된다.
 */
@Schema(description = "세션 사진 목록 응답")
public record PhotoListResponse(

        @Schema(description = "사진 목록 (최근 업로드 순)")
        List<PhotoResponse> content
) {
    public static PhotoListResponse of(List<PhotoResponse> content) {
        return new PhotoListResponse(content);
    }
}
