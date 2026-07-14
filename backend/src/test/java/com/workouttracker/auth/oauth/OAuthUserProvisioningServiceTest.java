package com.workouttracker.auth.oauth;

import com.workouttracker.user.AuthProvider;
import com.workouttracker.user.User;
import com.workouttracker.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 소셜 사용자 프로비저닝 3분기 검증 (스펙: 소셜 사용자 프로비저닝 / 같은 이메일 계정 자동 연동).
 *
 * <p>분기: (1) (provider,providerId) 기존 사용자 → 그대로 반환.
 * (2) 없음 + 검증 이메일 일치 기존 사용자 → 연동. (3) 둘 다 없음 → 신규 생성.
 * 이메일 없음(카카오) → 이메일 연동 스킵하고 신규 생성.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OAuthUserProvisioningService")
class OAuthUserProvisioningServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OAuthUserProvisioningService service;

    private static OAuthUserInfo info(AuthProvider provider, String id, String email, String nickname) {
        return new OAuthUserInfo(provider, id,
                Optional.ofNullable(email), Optional.ofNullable(nickname));
    }

    @Test
    @DisplayName("(1) 기존 소셜 사용자면 신규 생성/연동 없이 그대로 반환한다")
    void existingSocialUser() {
        User existing = User.ofSocial(AuthProvider.GOOGLE, "sub-1", "a@gmail.com", "a");
        when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "sub-1"))
                .thenReturn(Optional.of(existing));

        User result = service.provision(info(AuthProvider.GOOGLE, "sub-1", "a@gmail.com", "a"));

        assertThat(result).isSameAs(existing);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("(2) 같은 이메일의 기존 로컬 계정이 있으면 provider 를 연동한다 (신규 생성 없음)")
    void linksToExistingLocalAccountByEmail() {
        User local = User.builder().email("same@ex.com").passwordHash("h").nickname("로컬").build();
        when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "sub-2"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("same@ex.com")).thenReturn(Optional.of(local));

        User result = service.provision(info(AuthProvider.GOOGLE, "sub-2", "same@ex.com", "구글닉"));

        assertThat(result.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(result.getProviderId()).isEqualTo("sub-2");
        assertThat(result.isEmailVerified()).isTrue(); // 소셜 신원 확인 → 인증 처리
        assertThat(result.getPasswordHash()).isEqualTo("h"); // 로컬 비밀번호 유지
        verify(userRepository, never()).save(any()); // 영속 상태 변경은 트랜잭션 flush 에 위임
    }

    @Test
    @DisplayName("(3) 아무것도 없으면 신규 소셜 사용자를 생성한다 (비번 NULL, 이메일 인증됨)")
    void createsNewSocialUser() {
        when(userRepository.findByProviderAndProviderId(AuthProvider.NAVER, "nid-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@naver.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = service.provision(info(AuthProvider.NAVER, "nid-1", "new@naver.com", "네이버닉"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getProvider()).isEqualTo(AuthProvider.NAVER);
        assertThat(captor.getValue().getEmail()).isEqualTo("new@naver.com");
        assertThat(result.getPasswordHash()).isNull();
        assertThat(result.isEmailVerified()).isTrue();
        assertThat(result.getNickname()).isEqualTo("네이버닉");
    }

    @Test
    @DisplayName("이메일 없음(카카오) → 이메일 연동을 시도하지 않고 신규 생성한다")
    void noEmailSkipsEmailLinking() {
        when(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "kid-1"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = service.provision(info(AuthProvider.KAKAO, "kid-1", null, "카카오닉"));

        verify(userRepository, never()).findByEmail(anyString()); // 이메일 기반 연동 스킵
        assertThat(result.getEmail()).isNull();
        assertThat(result.getProvider()).isEqualTo(AuthProvider.KAKAO);
    }

    @Test
    @DisplayName("닉네임이 없으면 provider 기반 기본 닉네임을 생성한다")
    void generatesFallbackNickname() {
        when(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "kid-2"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = service.provision(info(AuthProvider.KAKAO, "kid-2", null, null));

        assertThat(result.getNickname()).isNotBlank();
        assertThat(result.getNickname().length()).isLessThanOrEqualTo(50); // DDL varchar(50)
    }
}
