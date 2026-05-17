package com.workouttracker.photo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * S3 PUT presigned URL 발급 요청 DTO.
 *
 * <p>출처: docs/design.md 3.5 POST /photos/presign
 *
 * <p>Bean Validation 으로 1차 검증 + PhotoService 에서 contentType 화이트리스트 추가 검증.
 */
@Schema(description = "S3 PUT presigned URL 요청")
public record PresignRequest(

        @Schema(description = "MIME 타입", example = "image/jpeg",
                allowableValues = {"image/jpeg", "image/png", "image/webp"})
        @NotBlank
        String contentType,

        @Schema(description = "업로드할 바이트 수 (1 ~ 10485760)", example = "524288")
        @NotNull
        @Min(value = 1, message = "sizeBytes 는 1 바이트 이상이어야 합니다.")
        @Max(value = 10_485_760L, message = "sizeBytes 는 10MB 이하여야 합니다.")
        Long sizeBytes
) {}
