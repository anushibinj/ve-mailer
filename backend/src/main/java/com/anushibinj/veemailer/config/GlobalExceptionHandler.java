package com.anushibinj.veemailer.config;

import com.anushibinj.veemailer.dto.ApiErrorResponse;
import com.anushibinj.veemailer.dto.WorkspaceConflictErrorResponse;
import com.anushibinj.veemailer.dto.WorkspaceDiscoveryErrorResponse;
import com.anushibinj.veemailer.exception.DuplicateWorkspaceException;
import com.anushibinj.veemailer.exception.WorkspaceDiscoveryFailedException;
import com.anushibinj.veemailer.exception.WorkspaceDiscoveryNotFoundException;
import com.anushibinj.veemailer.model.Workspace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleTooManyRequests(IllegalStateException ex) {
        ApiErrorResponse error = ApiErrorResponse.builder()
                .status(429)
                .error("Too Many Requests")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(429).body(error);
    }

    @ExceptionHandler(DuplicateWorkspaceException.class)
    public ResponseEntity<WorkspaceConflictErrorResponse> handleDuplicateWorkspace(DuplicateWorkspaceException ex) {
        log.warn("Rejected duplicate workspace creation/update: {}", ex.getMessage());
        Workspace existing = ex.getExistingWorkspace();
        WorkspaceConflictErrorResponse error = WorkspaceConflictErrorResponse.builder()
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .message("A workspace with the same Root URL, Shared Space ID, and Workspace ID already exists.")
                .existingWorkspace(WorkspaceConflictErrorResponse.ExistingWorkspaceRef.builder()
                        .id(existing.getId())
                        .name(existing.getTitle())
                        .rootUrl(existing.getRootUrl())
                        .sharedSpaceId(existing.getSharedSpaceId())
                        .workspaceId(existing.getWorkspaceId())
                        .build())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(WorkspaceDiscoveryNotFoundException.class)
    public ResponseEntity<WorkspaceDiscoveryErrorResponse> handleWorkspaceDiscoveryNotFound(
            WorkspaceDiscoveryNotFoundException ex) {
        log.warn("Workspace metadata discovery: {}", ex.getMessage());
        WorkspaceDiscoveryErrorResponse error = WorkspaceDiscoveryErrorResponse.builder()
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(ex.getMessage())
                .rawResponse(ex.getRawResponse())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(WorkspaceDiscoveryFailedException.class)
    public ResponseEntity<WorkspaceDiscoveryErrorResponse> handleWorkspaceDiscoveryFailed(
            WorkspaceDiscoveryFailedException ex) {
        log.warn("Workspace metadata discovery failed: {}", ex.getMessage());
        WorkspaceDiscoveryErrorResponse error = WorkspaceDiscoveryErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(ex.getMessage())
                .rawResponse(ex.getRawResponse())
                .build();
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Handled bad request: {}", ex.getMessage(), ex);
        ApiErrorResponse error = ApiErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ApiErrorResponse error = ApiErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("One or more fields have validation errors.")
                .fieldErrors(fieldErrors)
                .build();
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        ApiErrorResponse error = ApiErrorResponse.builder()
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Unauthorized")
                .message("Invalid email or password.")
                .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleUserNotFound(UsernameNotFoundException ex) {
        ApiErrorResponse error = ApiErrorResponse.builder()
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Unauthorized")
                .message("Invalid email or password.")
                .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        ApiErrorResponse error = ApiErrorResponse.builder()
                .status(HttpStatus.FORBIDDEN.value())
                .error("Forbidden")
                .message("You do not have permission to perform this action.")
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        ApiErrorResponse error = ApiErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred.")
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
