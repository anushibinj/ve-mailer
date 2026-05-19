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
 * Returns HTTP 403 with a JSON body when an authenticated user attempts an
 * action they are not permitted to perform (e.g., MEMBER trying to create a
 * workspace).  Does NOT indicate an authentication failure — the user's
 * identity is valid; they simply lack the required role or permission.
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
                .message("You do not have access to do that")
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
