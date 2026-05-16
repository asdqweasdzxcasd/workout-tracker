package com.workouttracker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workouttracker.common.error.ErrorCode;
import com.workouttracker.common.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * 인증되지 않은 요청에 대한 401 표준 JSON 응답을 작성한다.
 *
 * <p>Spring Security 의 기본 EntryPoint 는 HTML/리다이렉트 응답이라
 * 우리 표준 {@link ErrorResponse} 포맷에 맞춰 직접 작성한다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorResponse body = ErrorResponse.of(
                ErrorCode.UNAUTHORIZED,
                ErrorCode.UNAUTHORIZED.getDefaultMessage(),
                request.getRequestURI(),
                UUID.randomUUID().toString().substring(0, 8)
        );
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
