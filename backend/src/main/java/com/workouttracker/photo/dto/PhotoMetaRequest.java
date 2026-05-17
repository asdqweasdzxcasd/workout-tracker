package com.workouttracker.photo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * S3 업로드 완료 후 메타데이터 등록 요청.
 *
 * <p>출처: docs/design.md 3.5 POST /sessions/{id}/photos
 *
 * <p>서버는 s3Key 의 prefix 가 인증된 사용자의 prefix(users/{userId}/...) 인지 검증해
 * 다른 사용자 키 위변조를 거부한다.
 */
@Schema(description = "사진 메타데이터 등록 요청")
public record PhotoMetaRequest(

        @Schema(description = "presign 응답에서 받은 S3 키",
                example = "users/1/2026/05/abc123.jpg")
        @NotBlank
        @Size(max = 300)
        String s3Key,

        @Schema(description = "MIME 타입", example = "image/jpeg")
        @NotBlank
        String contentType,

        @Schema(description = "업로드된 바이트 수", example = "524288")
        @NotNull
        @Min(value = 1)
        @Max(value = 10_485_760L)
        Long sizeBytes
) {}
