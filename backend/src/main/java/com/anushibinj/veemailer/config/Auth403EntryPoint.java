package com.anushibinj.veemailer.config;

import com.anushibinj.veemailer.dto.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns HTTP 403 (not 401) for ALL unauthenticated requests:
 * missing JWT, expired JWT, invalid JWT, revoked session, etc.
 *
 * This normalises the authentication failure signal so the frontend
 * can reliably detect session expiry on a single status code.
 */
@Component
@RequiredArgsConstructor
public class Auth403EntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiErrorResponse error = ApiErrorResponse.builder()
                .status(HttpStatus.FORBIDDEN.value())
                .error("FORBIDDEN")
                .message("Authentication required")
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
