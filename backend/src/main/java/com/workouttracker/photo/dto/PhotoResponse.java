package com.workouttracker.photo.dto;

import com.workouttracker.photo.SessionPhoto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 사진 메타데이터 + 다운로드 presigned URL 응답.
 *
 * <p>출처: docs/design.md 3.5 (사진 응답), 5.4 워크플로우 다이어그램
 *
 * <p>downloadUrl 은 GET presigned URL (15분 만료) → 화면이 닫혀도 만료 후 자동 차단된다.
 */
@Schema(description = "사진 응답")
public record PhotoResponse(

        @Schema(description = "사진 ID", example = "10") Long id,

        @Schema(description = "세션 ID", example = "123") Long sessionId,

        @Schema(description = "S3 객체 키", example = "users/1/2026/05/abc123.jpg") String s3Key,

        @Schema(description = "MIME", example = "image/jpeg") String contentType,

        @Schema(description = "사이즈 (bytes)", example = "524288") Long sizeBytes,

        @Schema(description = "업로드 시각") OffsetDateTime uploadedAt,

        @Schema(description = "조회용 presigned URL (15분 만료)") String downloadUrl
) {
    public static PhotoResponse of(SessionPhoto photo, String downloadUrl) {
        return new PhotoResponse(
                photo.getId(),
                photo.getSessionId(),
                photo.getS3Key(),
                photo.getContentType(),
                photo.getSizeBytes(),
                photo.getUploadedAt(),
                downloadUrl
        );
    }
}
