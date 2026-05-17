package com.workouttracker.photo;

import com.workouttracker.common.error.BusinessException;
import com.workouttracker.common.error.ErrorCode;
import com.workouttracker.photo.dto.PhotoMetaRequest;
import com.workouttracker.photo.dto.PhotoResponse;
import com.workouttracker.photo.dto.PresignRequest;
import com.workouttracker.photo.dto.PresignResponse;
import com.workouttracker.session.WorkoutSession;
import com.workouttracker.session.WorkoutSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.net.URI;
import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PhotoService 단위 테스트.
 *
 * <p>S3 클라이언트 / Presigner 는 모두 Mockito 로 mock. 실제 AWS 호출 없음.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PhotoService 단위 테스트")
class PhotoServiceTest {

    @Mock private SessionPhotoRepository photoRepository;
    @Mock private WorkoutSessionRepository sessionRepository;
    @Mock private S3Presigner s3Presigner;
    @Mock private S3Client s3Client;

    @InjectMocks
    private PhotoService photoService;

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 999L;
    private static final Long SESSION_ID = 100L;
    private static final String BUCKET = "test-bucket";

    @BeforeEach
    void setUpProperties() {
        // @Value 주입 필드 - ReflectionTestUtils 로 세팅
        ReflectionTestUtils.setField(photoService, "bucket", BUCKET);
        ReflectionTestUtils.setField(photoService, "uploadExpiresSec", 300);
        ReflectionTestUtils.setField(photoService, "downloadExpiresSec", 900);
    }

    // ====================================================================
    // PRESIGN
    // ====================================================================

    @Test
    @DisplayName("presign 성공 - 키 형식 users/{userId}/{yyyy}/{MM}/{uuid}.{ext}")
    void presign_success() throws Exception {
        // given
        PresignRequest req = new PresignRequest("image/jpeg", 524_288L);
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        URL url = URI.create("https://test-bucket.s3.amazonaws.com/some-key?X-Amz-Signature=abc").toURL();
        when(presigned.url()).thenReturn(url);
        when(s3Presigner.presignPutObject(any(software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.class)))
                .thenReturn(presigned);

        // when
        PresignResponse resp = photoService.generatePresignedUploadUrl(USER_ID, req);

        // then
        assertThat(resp.uploadUrl()).startsWith("https://");
        assertThat(resp.expiresInSec()).isEqualTo(300);
        // 키 형식: users/1/{yyyy}/{MM}/{uuid}.jpg
        String currentYear = String.valueOf(LocalDate.now().getYear());
        assertThat(resp.s3Key())
                .startsWith("users/" + USER_ID + "/" + currentYear + "/")
                .endsWith(".jpg");
        assertThat(resp.s3Key().split("/")).hasSize(5);

        // S3 presigner 호출 시 contentType, contentLength 전달 검증
        ArgumentCaptor<software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest> captor =
                ArgumentCaptor.forClass(software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(captor.capture());
        PutObjectRequest putRequest = captor.getValue().putObjectRequest();
        assertThat(putRequest.bucket()).isEqualTo(BUCKET);
        assertThat(putRequest.contentType()).isEqualTo("image/jpeg");
        assertThat(putRequest.contentLength()).isEqualTo(524_288L);
    }

    @Test
    @DisplayName("presign 실패 - contentType 화이트리스트 위반")
    void presign_invalidContentType() {
        PresignRequest req = new PresignRequest("application/pdf", 1024L);

        assertThatThrownBy(() -> photoService.generatePresignedUploadUrl(USER_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(s3Presigner, never()).presignPutObject(any(software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.class));
    }

    @Test
    @DisplayName("presign 실패 - sizeBytes 10MB 초과")
    void presign_sizeTooLarge() {
        PresignRequest req = new PresignRequest("image/jpeg", 10L * 1024 * 1024 + 1);

        assertThatThrownBy(() -> photoService.generatePresignedUploadUrl(USER_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(s3Presigner, never()).presignPutObject(any(software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.class));
    }

    // ====================================================================
    // REGISTER METADATA
    // ====================================================================

    @Test
    @DisplayName("메타데이터 등록 - 세션 소유권 위반 시 NOT_FOUND")
    void register_sessionNotOwned() {
        PhotoMetaRequest req = new PhotoMetaRequest(
                "users/" + USER_ID + "/2026/05/abc.jpg", "image/jpeg", 1024L);
        when(sessionRepository.findByIdAndUserId(SESSION_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> photoService.registerUploadedPhoto(USER_ID, SESSION_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        verify(photoRepository, never()).save(any(SessionPhoto.class));
    }

    @Test
    @DisplayName("메타데이터 등록 - s3Key 가 다른 사용자의 prefix 면 VALIDATION_FAILED")
    void register_s3KeyOwnershipViolation() {
        WorkoutSession session = sessionForUser(USER_ID);
        when(sessionRepository.findByIdAndUserId(SESSION_ID, USER_ID))
                .thenReturn(Optional.of(session));

        // 다른 사용자 prefix 위변조 시도
        PhotoMetaRequest req = new PhotoMetaRequest(
                "users/" + OTHER_USER_ID + "/2026/05/abc.jpg", "image/jpeg", 1024L);

        assertThatThrownBy(() -> photoService.registerUploadedPhoto(USER_ID, SESSION_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(photoRepository, never()).save(any(SessionPhoto.class));
    }

    @Test
    @DisplayName("메타데이터 등록 성공 - 다운로드 presigned URL 포함 응답")
    void register_success() throws Exception {
        // given
        WorkoutSession session = sessionForUser(USER_ID);
        when(sessionRepository.findByIdAndUserId(SESSION_ID, USER_ID))
                .thenReturn(Optional.of(session));

        String s3Key = "users/" + USER_ID + "/2026/05/abc.jpg";
        PhotoMetaRequest req = new PhotoMetaRequest(s3Key, "image/jpeg", 1024L);

        // save 시 ID 부여 (sessionId 는 create() 에서 이미 세팅됨)
        when(photoRepository.save(any(SessionPhoto.class))).thenAnswer(invocation -> {
            SessionPhoto p = invocation.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 11L);
            return p;
        });

        // GET presigned URL mock
        PresignedGetObjectRequest presignedGet = mock(PresignedGetObjectRequest.class);
        URL url = URI.create("https://test-bucket.s3.amazonaws.com/" + s3Key + "?X-Amz-Signature=xyz").toURL();
        when(presignedGet.url()).thenReturn(url);
        when(s3Presigner.presignGetObject(any(software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.class)))
                .thenReturn(presignedGet);

        // when
        PhotoResponse resp = photoService.registerUploadedPhoto(USER_ID, SESSION_ID, req);

        // then
        assertThat(resp.id()).isEqualTo(11L);
        assertThat(resp.sessionId()).isEqualTo(SESSION_ID);
        assertThat(resp.s3Key()).isEqualTo(s3Key);
        assertThat(resp.contentType()).isEqualTo("image/jpeg");
        assertThat(resp.sizeBytes()).isEqualTo(1024L);
        assertThat(resp.downloadUrl()).contains("X-Amz-Signature");

        verify(photoRepository, times(1)).save(any(SessionPhoto.class));
    }

    // ====================================================================
    // DELETE
    // ====================================================================

    @Test
    @DisplayName("사진 삭제 - 소유권 위반 시 NOT_FOUND, S3 호출 없음")
    void delete_ownershipViolation() {
        when(photoRepository.findByIdAndUserId(1L, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> photoService.deletePhoto(USER_ID, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        verify(photoRepository, never()).delete(any(SessionPhoto.class));
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("사진 삭제 성공 - DB 삭제 후 S3 deleteObject best-effort 호출")
    void delete_success() {
        SessionPhoto photo = newPhoto(11L, "users/" + USER_ID + "/2026/05/abc.jpg");
        when(photoRepository.findByIdAndUserId(11L, USER_ID))
                .thenReturn(Optional.of(photo));

        photoService.deletePhoto(USER_ID, 11L);

        verify(photoRepository, times(1)).delete(photo);
        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client, times(1)).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo("users/" + USER_ID + "/2026/05/abc.jpg");
    }

    // ====================================================================
    // LIST
    // ====================================================================

    @Test
    @DisplayName("목록 조회 - 세션 소유권 위반 시 NOT_FOUND")
    void list_sessionNotOwned() {
        when(sessionRepository.findByIdAndUserId(SESSION_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> photoService.listSessionPhotos(USER_ID, SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("목록 조회 성공 - 각 항목 downloadUrl 포함")
    void list_success() throws Exception {
        when(sessionRepository.findByIdAndUserId(SESSION_ID, USER_ID))
                .thenReturn(Optional.of(sessionForUser(USER_ID)));

        SessionPhoto p1 = newPhoto(11L, "users/1/2026/05/a.jpg");
        SessionPhoto p2 = newPhoto(12L, "users/1/2026/05/b.jpg");
        when(photoRepository.findAllBySessionIdAndUserIdOrderByUploadedAtDesc(SESSION_ID, USER_ID))
                .thenReturn(List.of(p1, p2));

        PresignedGetObjectRequest presignedGet = mock(PresignedGetObjectRequest.class);
        URL url = URI.create("https://test-bucket.s3.amazonaws.com/x?X-Amz-Signature=abc").toURL();
        // lenient - 본 mock 은 두 번 호출되지만 동일 결과
        lenient().when(presignedGet.url()).thenReturn(url);
        when(s3Presigner.presignGetObject(any(software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.class)))
                .thenReturn(presignedGet);

        List<PhotoResponse> result = photoService.listSessionPhotos(USER_ID, SESSION_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(11L);
        assertThat(result.get(0).downloadUrl()).contains("X-Amz-Signature");
        assertThat(result.get(1).id()).isEqualTo(12L);
    }

    // ====================================================================
    // 헬퍼
    // ====================================================================

    private WorkoutSession sessionForUser(Long userId) {
        WorkoutSession s = WorkoutSession.builder()
                .userId(userId)
                .performedOn(LocalDate.now())
                .build();
        ReflectionTestUtils.setField(s, "id", SESSION_ID);
        return s;
    }

    private SessionPhoto newPhoto(Long id, String s3Key) {
        SessionPhoto p = SessionPhoto.create(
                sessionForUser(USER_ID), USER_ID, s3Key, "image/jpeg", 1024L);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }
}
