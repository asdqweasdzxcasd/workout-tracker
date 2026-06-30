package com.workouttracker.auth.email;

import com.workouttracker.common.error.BusinessException;
import com.workouttracker.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * E2E 테스트 지원 컨트롤러 — <b>운영(prod)에는 절대 노출되지 않는다</b>({@code @Profile("!prod")}).
 *
 * <p>Playwright E2E 는 SES/메일함을 거치지 않고 발급된 인증 코드를 알아야 한다.
 * {@code !prod} 프로필에서만 빈으로 등록되며, {@link TestVerificationCodeRecorder} 가 임시 적재한
 * 평문 코드를 그대로 돌려준다. 코드가 없으면(미발급/만료) 404 를 던진다.</p>
 *
 * <p>보안: {@link Hidden} 으로 OpenAPI 문서에서도 감춘다. SecurityConfig 의 permitAll 도
 * {@code !prod} 프로필 분기로만 추가되어, 운영에서는 경로 자체가 인증 대상이 되며 빈도 없다.</p>
 */
@Hidden
@RestController
@RequestMapping("/api/v1/test")
@org.springframework.context.annotation.Profile("!prod")
@RequiredArgsConstructor
public class TestSupportController {

    private final TestVerificationCodeRecorder recorder;

    /**
     * 마지막으로 발급된 평문 인증 코드 조회 (E2E 전용).
     *
     * @param email 코드 발급 대상 이메일 (서비스와 동일하게 정규화)
     * @return 평문 코드 문자열 (없으면 404)
     */
    @SecurityRequirements // 공개 (테스트 프로필 한정)
    @GetMapping("/last-verification-code")
    public ResponseEntity<String> lastVerificationCode(@RequestParam("email") String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String code = recorder.findLastCode(normalizedEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return ResponseEntity.ok(code);
    }
}
