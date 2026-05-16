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
