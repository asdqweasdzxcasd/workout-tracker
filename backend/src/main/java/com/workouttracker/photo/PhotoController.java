package com.workouttracker.photo;

import com.workouttracker.common.error.ErrorResponse;
import com.workouttracker.photo.dto.PhotoListResponse;
import com.workouttracker.photo.dto.PhotoMetaRequest;
import com.workouttracker.photo.dto.PhotoResponse;
import com.workouttracker.photo.dto.PresignRequest;
import com.workouttracker.photo.dto.PresignResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 인증샷 컨트롤러.
 *
 * <p>출처: docs/design.md 3.4 엔드포인트 목록
 *
 * <p>설계상 base path 가 둘로 나뉘므로 클래스 레벨 매핑은 두지 않고 메서드별 풀패스로 명시.
 * <ul>
 *   <li>POST   /api/v1/photos/presign</li>
 *   <li>GET    /api/v1/sessions/{sessionId}/photos</li>
 *   <li>POST   /api/v1/sessions/{sessionId}/photos</li>
 *   <li>DELETE /api/v1/photos/{photoId}</li>
 * </ul>
 */
@Tag(name = "Photo", description = "운동 인증샷 (S3 presigned 워크플로우)")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PhotoController {

    private final PhotoService photoService;

    @Operation(summary = "S3 PUT presigned URL 발급",
            description = "유효한 contentType / sizeBytes 인 경우 5분 만료의 PUT presigned URL 발급.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공",
                    content = @Content(schema = @Schema(implementation = PresignResponse.class))),
            @ApiResponse(responseCode = "400", description = "검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/photos/presign")
    public ResponseEntity<PresignResponse> presign(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PresignRequest request) {
        return ResponseEntity.ok(photoService.generatePresignedUploadUrl(userId, request));
    }

    @Operation(summary = "세션 사진 메타데이터 등록",
            description = "클라이언트가 S3 PUT 완료 후 호출. 세션 소유권 + s3Key prefix 본인 일치 검증.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공",
                    content = @Content(schema = @Schema(implementation = PhotoResponse.class))),
            @ApiResponse(responseCode = "400", description = "검증 실패 (s3Key prefix 등)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "세션 없음 또는 소유권 위반",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/sessions/{sessionId}/photos")
    @ResponseStatus(HttpStatus.CREATED)
    public PhotoResponse register(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "세션 ID", example = "123")
            @PathVariable("sessionId") Long sessionId,
            @Valid @RequestBody PhotoMetaRequest request) {
        return photoService.registerUploadedPhoto(userId, sessionId, request);
    }

    @Operation(summary = "세션 사진 목록 조회",
            description = "본인 세션의 사진들. 각 항목에 15분 만료 다운로드 presigned URL 포함.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공",
                    content = @Content(schema = @Schema(implementation = PhotoListResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "세션 없음 또는 소유권 위반",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/sessions/{sessionId}/photos")
    public ResponseEntity<PhotoListResponse> list(
            @AuthenticationPrincipal Long userId,
            @PathVariable("sessionId") Long sessionId) {
        List<PhotoResponse> content = photoService.listSessionPhotos(userId, sessionId);
        return ResponseEntity.ok(PhotoListResponse.of(content));
    }

    @Operation(summary = "사진 삭제",
            description = "DB 에서 즉시 제거하고 S3 객체 삭제는 best-effort. 소유권 위반 시 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "사진 없음 또는 소유권 위반",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable("photoId") Long photoId) {
        photoService.deletePhoto(userId, photoId);
    }
}
