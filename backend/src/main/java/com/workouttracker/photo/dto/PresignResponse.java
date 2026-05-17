package com.workouttracker.photo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * S3 PUT presigned URL 발급 응답.
 *
 * <p>출처: docs/design.md 3.5 POST /photos/presign Response
 *
 * <p>클라이언트는 {@code uploadUrl} 에 fetch(method='PUT', Content-Type 동일) 로 직접 업로드한 뒤
 * {@code s3Key} 를 다시 POST /sessions/{id}/photos 로 보내 메타데이터를 등록한다.
 */
@Schema(description = "S3 PUT presigned URL 응답")
public record PresignResponse(

        @Schema(description = "PUT 으로 호출할 S3 presigned URL")
        String uploadUrl,

        @Schema(description = "S3 객체 키 (메타데이터 등록 시 그대로 전송)",
                example = "users/1/2026/05/abc123.jpg")
        String s3Key,

        @Schema(description = "presigned URL 유효 시간 (초)", example = "300")
        int expiresInSec
) {}
