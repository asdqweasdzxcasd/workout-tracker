package com.workouttracker.photo;

import com.workouttracker.session.WorkoutSession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 운동 세션 인증샷 메타데이터 엔티티.
 *
 * <p>출처: docs/design.md 2.2 session_photos DDL
 *
 * <p>설계 결정 사항
 * <ul>
 *   <li>실제 바이너리는 S3 에 저장하고 본 엔티티는 메타데이터 (s3Key 등) 만 보유한다.</li>
 *   <li>업로드는 presigned PUT 으로 클라이언트가 S3 에 직접 → 트랜잭션 외부.</li>
 *   <li>session 은 @ManyToOne LAZY - 세션 화면에서 사진 목록 조회 시 N+1 회피.</li>
 *   <li>userId 는 FK 컬럼으로만 유지하고 ManyToOne 매핑하지 않는다 (User 엔티티 의존 최소화).</li>
 *   <li>uploadedAt 은 @PrePersist 로 KST 기준 시간 자동 세팅.</li>
 * </ul>
 */
@Entity
@Table(name = "session_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, insertable = false, updatable = false)
    private Long sessionId;

    /**
     * 부모 세션 - LAZY 로 N+1 회피.
     * insertable/updatable=false 로 두고 session_id 컬럼은 위 필드를 활용.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private WorkoutSession session;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "s3_key", nullable = false, length = 300)
    private String s3Key;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private OffsetDateTime uploadedAt;

    /**
     * 메타데이터 등록용 정적 팩토리 메서드.
     *
     * @param session     소유 세션 (검증된 본인 세션)
     * @param userId      업로더 ID (s3Key prefix 와 일치해야 한다 - 보안)
     * @param s3Key       S3 객체 키 (users/{userId}/{yyyy}/{MM}/{uuid}.{ext})
     * @param contentType MIME (image/jpeg, image/png, image/webp)
     * @param sizeBytes   업로드된 바이트 수 (1 ~ 10MB)
     */
    public static SessionPhoto create(
            WorkoutSession session,
            Long userId,
            String s3Key,
            String contentType,
            Long sizeBytes) {
        SessionPhoto p = new SessionPhoto();
        p.session = session;
        // insertable=false 인 sessionId 도 즉시 읽을 수 있도록 동기화. 저장 후에도 일관성 유지.
        p.sessionId = session != null ? session.getId() : null;
        p.userId = userId;
        p.s3Key = s3Key;
        p.contentType = contentType;
        p.sizeBytes = sizeBytes;
        return p;
    }

    @PrePersist
    void onCreate() {
        this.uploadedAt = OffsetDateTime.now();
    }
}
