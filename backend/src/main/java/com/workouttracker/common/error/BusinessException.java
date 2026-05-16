package com.workouttracker.common.error;

/**
 * 도메인/비즈니스 규칙 위반을 표현하는 사용자 정의 예외.
 *
 * <p>{@link ErrorCode} 만 지정하면 기본 메시지가 사용되고, 추가 컨텍스트가 필요하면
 * 메시지를 직접 지정할 수 있다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
