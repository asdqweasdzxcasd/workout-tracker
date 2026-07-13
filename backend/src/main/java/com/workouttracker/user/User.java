package com.workouttracker.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 사용자 엔티티.
 *
 * <p>출처: docs/design.md 2.2 users DDL, D.3 OAuth(openspec: oauth-social-login)
 *
 * <p>불변식(설계 5): 로컬 가입자 = {@code passwordHash != null && provider == null},
 * 소셜 가입자 = {@code passwordHash == null && provider != null}. 소셜 가입자는
 * 제공자가 이미 이메일을 검증했으므로 {@code emailVerified = true} 로 생성된다.
 */
@Entity
@Table(name = "users", uniqueConstraints =
        @UniqueConstraint(name = "uq_users_provider", columnNames = {"provider", "provider_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 카카오 등 이메일 미동의 소셜 가입자는 NULL 가능. 로컬 가입은 앱 검증이 필수로 요구. */
    @Column(name = "email", unique = true, length = 255)
    private String email;

    /** 소셜 가입자는 NULL (비밀번호 없음). 로컬 가입자만 채워진다. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    /** 소셜 제공자. NULL = 로컬 가입. */
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 20)
    private AuthProvider provider;

    /** 제공자 내 고유 사용자 ID. 로컬 가입자는 NULL. */
    @Column(name = "provider_id", length = 255)
    private String providerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Builder
    private User(String email, String passwordHash, String nickname) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
    }

    private User(String email, String nickname, AuthProvider provider, String providerId) {
        this.email = email;
        this.nickname = nickname;
        this.provider = provider;
        this.providerId = providerId;
        this.emailVerified = true; // 소셜 제공자가 이미 검증한 신원 → 이메일 인증(D.2) 스킵
    }

    /**
     * 소셜 가입 사용자 생성 팩토리.
     *
     * @param email 제공자가 준 이메일(카카오 등은 없을 수 있음 → NULL 허용은 호출부 정책에 따름)
     */
    public static User ofSocial(AuthProvider provider, String providerId, String email, String nickname) {
        return new User(email, nickname, provider, providerId);
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    /** 이메일 인증 완료 처리 (도메인 메서드 — setter 미노출). */
    public void markEmailVerified() {
        this.emailVerified = true;
    }

    /**
     * 기존 계정에 소셜 제공자를 연동 (같은 이메일 자동 연동, 설계 4).
     *
     * <p>이미 다른 제공자가 연동돼 있으면 덮어쓰지 않는다(D.3는 단일 provider 컬럼,
     * 다중 연동은 D.4에서 링크 테이블로 승격). 소셜로 신원이 확인됐으므로 이메일도 인증 처리.
     */
    public void linkSocial(AuthProvider provider, String providerId) {
        if (this.provider == null) {
            this.provider = provider;
            this.providerId = providerId;
        }
        this.emailVerified = true;
    }
}
