package com.workouttracker.common.error;

import org.springframework.http.HttpStatus;

/**
 * 비즈니스 / 시스템 에러 코드 정의.
 *
 * <p>각 코드는 HTTP 상태 코드와 사용자에게 노출할 기본 메시지를 함께 보유한다.
 * 실제 응답은 {@link com.workouttracker.common.error.ErrorResponse} 로 직렬화된다.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값 검증에 실패했습니다."),
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    // Refresh Token 관련
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 유효하지 않습니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 만료되었습니다. 다시 로그인해주세요."),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED,
            "리프레시 토큰 재사용이 감지되어 모든 세션이 종료되었습니다. 다시 로그인해주세요."),
    // 이메일 인증 관련 (D.2)
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "이메일 인증이 필요합니다. 메일함을 확인해주세요."),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "인증 코드가 올바르지 않습니다."),
    VERIFICATION_CODE_EXPIRED(HttpStatus.GONE, "인증 코드가 만료되었습니다. 다시 요청해주세요."),
    TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS,
            "인증 시도 횟수를 초과했습니다. 코드를 다시 요청해주세요."),
    RESEND_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS,
            "인증 메일 재발송 요청이 너무 잦습니다. 잠시 후 다시 시도해주세요."),
    // OAuth 소셜 로그인 (D.3)
    UNSUPPORTED_OAUTH_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 로그인 제공자입니다."),
    INVALID_OAUTH_EXCHANGE_CODE(HttpStatus.UNAUTHORIZED,
            "소셜 로그인 코드가 유효하지 않거나 만료되었습니다. 다시 로그인해주세요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "대상 리소스를 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
