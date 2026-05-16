package com.workouttracker.common.error;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 전역 예외 처리기.
 *
 * <p>도메인 예외/검증 실패/인증 실패/그 외 시스템 오류를
 * 표준 {@link ErrorResponse} 로 변환하여 응답한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 비즈니스 예외 - ErrorCode 의 HttpStatus 그대로 매핑 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        log.warn("[BusinessException] code={} message={} path={}", code.name(), ex.getMessage(), request.getRequestURI());
        ErrorResponse body = ErrorResponse.of(code, ex.getMessage(), request.getRequestURI(), newTraceId());
        return ResponseEntity.status(code.getStatus()).body(body);
    }

    /** @Valid 검증 실패 - 400 VALIDATION_FAILED */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String detail = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining(", "));
        log.warn("[ValidationException] errors={} path={}", detail, request.getRequestURI());
        ErrorResponse body = ErrorResponse.of(
                ErrorCode.VALIDATION_FAILED,
                detail.isBlank() ? ErrorCode.VALIDATION_FAILED.getDefaultMessage() : detail,
                request.getRequestURI(),
                newTraceId()
        );
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus()).body(body);
    }

    /** Spring Security 인증 예외 - 401 UNAUTHORIZED */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuth(AuthenticationException ex, HttpServletRequest request) {
        log.warn("[AuthenticationException] message={} path={}", ex.getMessage(), request.getRequestURI());
        ErrorResponse body = ErrorResponse.of(
                ErrorCode.UNAUTHORIZED,
                ErrorCode.UNAUTHORIZED.getDefaultMessage(),
                request.getRequestURI(),
                newTraceId()
        );
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED.getStatus()).body(body);
    }

    /** 권한 거부 - 403 (현재 시점에서는 사실상 사용 안 되지만 안전망) */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("[AccessDeniedException] message={} path={}", ex.getMessage(), request.getRequestURI());
        // 403 으로 직접 응답 (NOT_FOUND 로 숨기는 정책은 컨트롤러/서비스에서 명시적으로 처리)
        ErrorResponse body = new ErrorResponse(
                java.time.OffsetDateTime.now(),
                403,
                "FORBIDDEN",
                "권한이 없습니다.",
                request.getRequestURI(),
                newTraceId()
        );
        return ResponseEntity.status(403).body(body);
    }

    /** 그 외 모든 예외 - 500 INTERNAL_ERROR */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception ex, HttpServletRequest request) {
        log.error("[UnhandledException] path={}", request.getRequestURI(), ex);
        ErrorResponse body = ErrorResponse.of(
                ErrorCode.INTERNAL_ERROR,
                ErrorCode.INTERNAL_ERROR.getDefaultMessage(),
                request.getRequestURI(),
                newTraceId()
        );
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus()).body(body);
    }

    private String formatFieldError(FieldError fe) {
        return "%s: %s".formatted(fe.getField(), fe.getDefaultMessage());
    }

    private String newTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
