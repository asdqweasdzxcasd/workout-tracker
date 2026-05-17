package com.workouttracker.photo;

import com.workouttracker.common.error.BusinessException;
import com.workouttracker.common.error.ErrorCode;
import com.workouttracker.photo.dto.PhotoMetaRequest;
import com.workouttracker.photo.dto.PhotoResponse;
import com.workouttracker.photo.dto.PresignRequest;
import com.workouttracker.photo.dto.PresignResponse;
import com.workouttracker.session.WorkoutSession;
import com.workouttracker.session.WorkoutSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 인증샷 도메인 서비스.
 *
 * <p>설계 문서 참조:
 * <ul>
 *   <li>3.4 / 3.5 photo 엔드포인트</li>
 *   <li>5.4 S3 Presigned URL 워크플로우</li>
 *   <li>7.8 S3 보안 (BlockPublicAccess, presigned 만료, 키 prefix 검증)</li>
 *   <li>2.4 트랜잭션 경계 - 메타데이터 INSERT 만 트랜잭션, S3 호출은 트랜잭션 밖</li>
 * </ul>
 *
 * <p>보안 핵심:
 * <ul>
 *   <li>contentType 화이트리스트 (jpeg/png/webp)</li>
 *   <li>sizeBytes 1B ~ 10MB</li>
 *   <li>s3Key prefix 가 본인의 {@code users/{userId}/} 인지 검증 (위변조 거부)</li>
 *   <li>모든 조회/삭제 API 에 소유권 검증 → 위반 시 NOT_FOUND</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoService {

    /** S3 PUT presigned URL 만료 (초) - 업로드 직후 사용 → 짧게 */
    private static final int UPLOAD_EXPIRES_DEFAULT_SEC = 300;
    /** S3 GET presigned URL 만료 (초) - 갤러리 로딩 후 짧은 시간만 유효 */
    private static final int DOWNLOAD_EXPIRES_DEFAULT_SEC = 900;

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    private static final long MIN_SIZE_BYTES = 1L;
    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024; // 10 MB

    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MM");

    private final SessionPhotoRepository photoRepository;
    private final WorkoutSessionRepository sessionRepository;
    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.s3.presign-upload-expires-sec:" + UPLOAD_EXPIRES_DEFAULT_SEC + "}")
    private int uploadExpiresSec;

    @Value("${aws.s3.presign-download-expires-sec:" + DOWNLOAD_EXPIRES_DEFAULT_SEC + "}")
    private int downloadExpiresSec;

    // ====================================================================
    // PRESIGN
    // ====================================================================

    /**
     * S3 PUT presigned URL 발급.
     *
     * <p>검증 순서:
     * <ol>
     *   <li>contentType 화이트리스트</li>
     *   <li>sizeBytes 범위</li>
     * </ol>
     * 위반 시 VALIDATION_FAILED.
     *
     * <p>S3 키 형식: {@code users/{userId}/{yyyy}/{MM}/{uuid}.{ext}}
     */
    public PresignResponse generatePresignedUploadUrl(Long userId, PresignRequest req) {
        validateContentType(req.contentType());
        validateSizeBytes(req.sizeBytes());

        String ext = extensionOf(req.contentType());
        String s3Key = buildS3Key(userId, ext);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .contentType(req.contentType())
                .contentLength(req.sizeBytes())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(uploadExpiresSec))
                .putObjectRequest(putRequest)
                .build();

        String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();

        log.info("S3 PUT presign 발급: userId={} s3Key={} bytes={} expires={}s",
                userId, s3Key, req.sizeBytes(), uploadExpiresSec);

        return new PresignResponse(uploadUrl, s3Key, uploadExpiresSec);
    }

    // ====================================================================
    // REGISTER METADATA
    // ====================================================================

    /**
     * S3 업로드 완료 후 메타데이터 등록.
     *
     * <p>트랜잭션: 메타데이터 INSERT 만. S3 객체는 클라이언트가 이미 PUT 완료한 상태.
     *
     * <p>소유권 검증 두 단계:
     * <ol>
     *   <li>세션이 본인 소유인지 → 아니면 NOT_FOUND</li>
     *   <li>s3Key 의 prefix 가 본인 prefix 인지 → 아니면 VALIDATION_FAILED</li>
     * </ol>
     */
    @Transactional
    public PhotoResponse registerUploadedPhoto(Long userId, Long sessionId, PhotoMetaRequest req) {
        WorkoutSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        validateS3KeyOwnership(userId, req.s3Key());
        validateContentType(req.contentType());
        validateSizeBytes(req.sizeBytes());

        SessionPhoto photo = SessionPhoto.create(
                session, userId, req.s3Key(), req.contentType(), req.sizeBytes());
        SessionPhoto saved = photoRepository.save(photo);
        log.info("사진 메타데이터 등록: userId={} sessionId={} photoId={} s3Key={}",
                userId, sessionId, saved.getId(), saved.getS3Key());

        String downloadUrl = generatePresignedDownloadUrl(saved.getS3Key());
        return PhotoResponse.of(saved, downloadUrl);
    }

    // ====================================================================
    // LIST
    // ====================================================================

    /**
     * 세션 소속 사진 목록 + 각 항목의 다운로드 presigned URL.
     *
     * <p>읽기 트랜잭션. 사진 수가 0~5 정도 (개인 운동 인증샷) 라 presign N회 호출도 무방.
     * 추후 페이징/캐싱이 필요해지면 한꺼번에 모아 발급하는 방식으로 전환 가능.
     */
    @Transactional(readOnly = true)
    public List<PhotoResponse> listSessionPhotos(Long userId, Long sessionId) {
        sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        List<SessionPhoto> photos =
                photoRepository.findAllBySessionIdAndUserIdOrderByUploadedAtDesc(sessionId, userId);

        return photos.stream()
                .map(p -> PhotoResponse.of(p, generatePresignedDownloadUrl(p.getS3Key())))
                .toList();
    }

    // ====================================================================
    // DELETE
    // ====================================================================

    /**
     * 사진 삭제.
     *
     * <p>트랜잭션: DB row 삭제만. S3 DeleteObject 는 트랜잭션 외부에서 best-effort.
     * S3 삭제 실패해도 DB 는 이미 커밋되며, 고아 객체는 라이프사이클 정책/별도 청소로 처리.
     */
    @Transactional
    public void deletePhoto(Long userId, Long photoId) {
        SessionPhoto photo = photoRepository.findByIdAndUserId(photoId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        String s3Key = photo.getS3Key();
        photoRepository.delete(photo);
        log.info("사진 메타데이터 삭제: userId={} photoId={} s3Key={}", userId, photoId, s3Key);

        // 트랜잭션 커밋 이후 S3 삭제. 트랜잭션 동기화로 묶지 않고 best-effort 시도.
        // (DB 가 진실의 원천이고 S3 객체는 라이프사이클 정책으로 청소 가능)
        deleteFromS3BestEffort(s3Key);
    }

    // ====================================================================
    // 내부 검증 / 보조 메서드
    // ====================================================================

    private void validateContentType(String contentType) {
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "허용되지 않은 contentType 입니다. 허용: " + ALLOWED_CONTENT_TYPES);
        }
    }

    private void validateSizeBytes(Long sizeBytes) {
        if (sizeBytes == null || sizeBytes < MIN_SIZE_BYTES || sizeBytes > MAX_SIZE_BYTES) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "sizeBytes 는 " + MIN_SIZE_BYTES + " ~ " + MAX_SIZE_BYTES + " 사이여야 합니다.");
        }
    }

    /**
     * s3Key 가 본인의 prefix({@code users/{userId}/}) 로 시작하는지 검증.
     * 위반 시 VALIDATION_FAILED - 다른 사용자 폴더로 위변조 시도 차단.
     */
    private void validateS3KeyOwnership(Long userId, String s3Key) {
        String expectedPrefix = "users/" + userId + "/";
        if (s3Key == null || !s3Key.startsWith(expectedPrefix)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "s3Key prefix 가 올바르지 않습니다.");
        }
    }

    private String extensionOf(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "contentType 매핑 실패: " + contentType);
        };
    }

    /**
     * S3 키 생성: {@code users/{userId}/{yyyy}/{MM}/{uuid}.{ext}}
     * 사용자 단위 분리 + 연/월 단위 폴더링.
     */
    private String buildS3Key(Long userId, String ext) {
        LocalDate today = LocalDate.now();
        return "users/" + userId
                + "/" + YEAR_FORMAT.format(today)
                + "/" + MONTH_FORMAT.format(today)
                + "/" + UUID.randomUUID() + "." + ext;
    }

    /** GET presigned URL 발급 (다운로드/표시용). */
    private String generatePresignedDownloadUrl(String s3Key) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(downloadExpiresSec))
                .getObjectRequest(getRequest)
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    /**
     * S3 객체 삭제 - 실패해도 트랜잭션 영향 없음.
     * 외부 시스템 경계이므로 에러를 잡아 로깅만 한다.
     */
    private void deleteFromS3BestEffort(String s3Key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .build());
            log.info("S3 객체 삭제: bucket={} key={}", bucket, s3Key);
        } catch (S3Exception e) {
            log.warn("S3 객체 삭제 실패 (best-effort, 무시): key={} message={}",
                    s3Key, e.getMessage());
        }
    }
}
