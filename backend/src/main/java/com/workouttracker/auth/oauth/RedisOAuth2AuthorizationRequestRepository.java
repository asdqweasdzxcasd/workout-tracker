package com.workouttracker.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;

import java.time.Duration;
import java.util.Base64;

/**
 * OAuth2 authorization request(state 포함) 의 Redis 저장소 (설계 3.3).
 *
 * <p>기본 구현은 HTTP 세션에 state 를 저장하는데, 우리 서버는 STATELESS + ECS 다중
 * 태스크(ALB 분산)라 세션을 쓸 수 없다 → state 를 키로 Redis 에 공유 저장한다.
 * 콜백이 어느 태스크로 오든 state 검증 가능(스펙: OAuth 콜백 처리 및 state 검증).
 *
 * <p>TTL 5분 — authorize 시작 후 5분 내 콜백만 유효. 만료/불일치 시 로드 실패
 * → Spring 이 authorization_request_not_found 로 인증 실패 처리(401 경로).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String KEY_PREFIX = "oauth2:authreq:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        if (state == null || state.isBlank()) {
            return null;
        }
        String serialized = redisTemplate.opsForValue().get(KEY_PREFIX + state);
        return serialized == null ? null : deserialize(serialized);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            // 계약상 null 저장 = 기존 요청 제거
            String state = request.getParameter(OAuth2ParameterNames.STATE);
            if (state != null) {
                redisTemplate.delete(KEY_PREFIX + state);
            }
            return;
        }
        redisTemplate.opsForValue().set(
                KEY_PREFIX + authorizationRequest.getState(),
                serialize(authorizationRequest),
                TTL);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        if (state == null || state.isBlank()) {
            return null;
        }
        // 원자적 GET+DEL — state 1회용 보장 (재사용/리플레이 방지)
        String serialized = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + state);
        return serialized == null ? null : deserialize(serialized);
    }

    private String serialize(OAuth2AuthorizationRequest request) {
        return Base64.getEncoder().encodeToString(SerializationUtils.serialize(request));
    }

    // JDK 역직렬화 deprecation: 신뢰 불가 데이터엔 위험하지만, 이 값은 우리 서버가
    // 직접 직렬화해 사설 Redis 에 넣은 것만 읽는다(외부 입력 아님) → 사용 허용.
    @SuppressWarnings("deprecation")
    private OAuth2AuthorizationRequest deserialize(String value) {
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            return (OAuth2AuthorizationRequest) SerializationUtils.deserialize(bytes);
        } catch (RuntimeException e) {
            // 역직렬화 실패(버전 변경 등)는 "요청 없음"과 동일하게 인증 실패로 흘린다
            log.warn("authorization request 역직렬화 실패: {}", e.toString());
            return null;
        }
    }
}
