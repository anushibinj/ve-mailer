package com.anushibinj.veemailer.config;

import com.anushibinj.veemailer.dto.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns HTTP 403 with a JSON body for filter-level access denial
 * (e.g., an authenticated user hitting a URL that requires a role they
 * do not have, as resolved by Spring Security's filter chain).
 *
 * Controller-level AccessDeniedException (from @PreAuthorize or manual
 * enforceOwnership checks) is handled by GlobalExceptionHandler instead.
 */
@Component
@RequiredArgsConstructor
public class Auth403AccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiErrorResponse error = ApiErrorResponse.builder()
                .status(HttpStatus.FORBIDDEN.value())
                .error("FORBIDDEN")
                .message("Access denied")
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
