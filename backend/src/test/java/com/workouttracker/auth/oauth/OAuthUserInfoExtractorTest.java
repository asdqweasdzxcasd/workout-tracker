package com.workouttracker.auth.oauth;

import com.workouttracker.user.AuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * provider 별 UserInfo 정규화 검증 (스펙: Provider별 사용자 정보 정규화).
 *
 * <p>각 provider 의 실제 응답 형태를 본뜬 맵으로 extractor 를 검증한다.
 * 핵심 케이스: 정상 매핑 / 필수 ID 누락 / 이메일 미검증·미제공(자동연동 방지).
 */
@DisplayName("OAuth UserInfo 정규화")
class OAuthUserInfoExtractorTest {

    @Nested
    @DisplayName("구글 (OIDC)")
    class Google {

        private final GoogleUserInfoExtractor extractor = new GoogleUserInfoExtractor();

        @Test
        @DisplayName("sub/email/name 을 공통 표현으로 매핑한다")
        void extractsAll() {
            OAuthUserInfo info = extractor.extract(Map.of(
                    "sub", "google-sub-1",
                    "email", "user@gmail.com",
                    "email_verified", true,
                    "name", "홍길동"
            ));

            assertThat(info.provider()).isEqualTo(AuthProvider.GOOGLE);
            assertThat(info.providerId()).isEqualTo("google-sub-1");
            assertThat(info.email()).contains("user@gmail.com");
            assertThat(info.nickname()).contains("홍길동");
        }

        @Test
        @DisplayName("email_verified=false 면 이메일을 버린다 (자동연동 방지)")
        void unverifiedEmailDropped() {
            OAuthUserInfo info = extractor.extract(Map.of(
                    "sub", "google-sub-2",
                    "email", "user@gmail.com",
                    "email_verified", false
            ));

            assertThat(info.email()).isEmpty();
        }

        @Test
        @DisplayName("sub 가 없으면 실패한다")
        void missingSubThrows() {
            assertThatThrownBy(() -> extractor.extract(Map.of("email", "x@y.com")))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("네이버")
    class Naver {

        private final NaverUserInfoExtractor extractor = new NaverUserInfoExtractor();

        @Test
        @DisplayName("response 중첩 구조에서 id/email/nickname 을 꺼낸다")
        void extractsFromNestedResponse() {
            OAuthUserInfo info = extractor.extract(Map.of(
                    "resultcode", "00",
                    "message", "success",
                    "response", Map.of(
                            "id", "naver-id-1",
                            "email", "user@naver.com",
                            "nickname", "네이버닉"
                    )
            ));

            assertThat(info.provider()).isEqualTo(AuthProvider.NAVER);
            assertThat(info.providerId()).isEqualTo("naver-id-1");
            assertThat(info.email()).contains("user@naver.com");
            assertThat(info.nickname()).contains("네이버닉");
        }

        @Test
        @DisplayName("attributes 가 이미 response 맵인 경우도 처리한다 (user-name-attribute=response)")
        void extractsFromFlattenedResponse() {
            OAuthUserInfo info = extractor.extract(Map.of(
                    "id", "naver-id-2",
                    "email", "user2@naver.com"
            ));

            assertThat(info.providerId()).isEqualTo("naver-id-2");
            assertThat(info.email()).contains("user2@naver.com");
            assertThat(info.nickname()).isEmpty();
        }

        @Test
        @DisplayName("id 가 없으면 실패한다")
        void missingIdThrows() {
            assertThatThrownBy(() -> extractor.extract(Map.of("response", Map.of("email", "x@y.com"))))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("카카오")
    class Kakao {

        private final KakaoUserInfoExtractor extractor = new KakaoUserInfoExtractor();

        @Test
        @DisplayName("숫자 id 를 문자열로, 검증된 이메일과 프로필 닉네임을 매핑한다")
        void extractsAll() {
            OAuthUserInfo info = extractor.extract(Map.of(
                    "id", 123456789L,
                    "kakao_account", Map.of(
                            "email", "user@kakao.com",
                            "is_email_verified", true,
                            "profile", Map.of("nickname", "카카오닉")
                    )
            ));

            assertThat(info.provider()).isEqualTo(AuthProvider.KAKAO);
            assertThat(info.providerId()).isEqualTo("123456789");
            assertThat(info.email()).contains("user@kakao.com");
            assertThat(info.nickname()).contains("카카오닉");
        }

        @Test
        @DisplayName("이메일 동의항목이 없으면(선택 미제공) 이메일 없이 정규화된다")
        void noEmailConsent() {
            OAuthUserInfo info = extractor.extract(Map.of(
                    "id", 987654321L,
                    "properties", Map.of("nickname", "프로퍼티닉")
            ));

            assertThat(info.providerId()).isEqualTo("987654321");
            assertThat(info.email()).isEmpty();
            assertThat(info.nickname()).contains("프로퍼티닉"); // properties fallback
        }

        @Test
        @DisplayName("이메일이 있어도 is_email_verified=false 면 버린다")
        void unverifiedEmailDropped() {
            OAuthUserInfo info = extractor.extract(Map.of(
                    "id", 1L,
                    "kakao_account", Map.of(
                            "email", "user@kakao.com",
                            "is_email_verified", false
                    )
            ));

            assertThat(info.email()).isEmpty();
        }

        @Test
        @DisplayName("id 가 없으면 실패한다")
        void missingIdThrows() {
            assertThatThrownBy(() -> extractor.extract(Map.of("kakao_account", Map.of())))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
