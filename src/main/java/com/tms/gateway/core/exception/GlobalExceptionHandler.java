package com.tms.gateway.core.exception;

import com.tms.gateway.core.dto.ApiResponse;
import jakarta.persistence.EntityNotFoundException;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for all REST endpoints.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(com.tms.gateway.modules.compliance.service.ComplianceValidationException.class)
    public ResponseEntity<Map<String, Object>> handleComplianceValidation(
            com.tms.gateway.modules.compliance.service.ComplianceValidationException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(Map.of("code", 422, "message", ex.getMessage(), "errors", ex.getErrors()));
    }

    @ExceptionHandler({org.springframework.web.bind.ServletRequestBindingException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(Exception ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, "Malformed request or missing required header"));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        Map<String, Object> body = new HashMap<>();
        body.put("code", 422);
        body.put("message", "Validation failed");
        body.put("errors", errors);
        return ResponseEntity.unprocessableEntity().body(body);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(401, "Invalid credentials"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, ex.getMessage()));
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.error(ex.getStatus().value(), ex.getMessage()));
    }

    @ExceptionHandler(GrpcException.class)
    public ResponseEntity<Map<String, Object>> handleGrpcException(GrpcException ex) {
        // Map gRPC status codes to appropriate HTTP status codes
        HttpStatus status = switch (ex.getGrpcStatus()) {
            case "UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "DEADLINE_EXCEEDED" -> HttpStatus.GATEWAY_TIMEOUT;
            case "UNIMPLEMENTED" -> HttpStatus.NOT_IMPLEMENTED;
            case "PERMISSION_DENIED" -> HttpStatus.FORBIDDEN;
            case "UNAUTHENTICATED" -> HttpStatus.UNAUTHORIZED;
            case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "ALREADY_EXISTS" -> HttpStatus.CONFLICT;
            case "INVALID_ARGUMENT" -> HttpStatus.BAD_REQUEST;
            case "RESOURCE_EXHAUSTED" -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_GATEWAY;
        };

        String userMessage = switch (ex.getGrpcStatus()) {
            case "UNAVAILABLE" -> "Service temporarily unavailable. Please try again.";
            case "DEADLINE_EXCEEDED" -> "Request timed out. Please try again.";
            case "UNIMPLEMENTED" -> "This feature is not available.";
            default -> ex.getMessage();
        };

        Map<String, Object> body = new HashMap<>();
        body.put("code", status.value());
        body.put("message", userMessage);
        body.put("error", ex.toMap());
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(403, "Forbidden"));
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccess(org.springframework.dao.DataAccessException ex) {
        log.error("Database error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Database operation failed. Please try again."));
    }

    @ExceptionHandler(io.grpc.StatusRuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleGrpcStatusRuntime(io.grpc.StatusRuntimeException ex) {
        log.error("Unhandled gRPC error: {}", ex.getStatus(), ex);
        HttpStatus status = switch (ex.getStatus().getCode()) {
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;
            case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_GATEWAY;
        };
        String message = switch (ex.getStatus().getCode()) {
            case UNAVAILABLE -> "Service temporarily unavailable. Please try again.";
            case DEADLINE_EXCEEDED -> "Request timed out. Please try again.";
            default -> "Service communication error: " + ex.getStatus().getCode();
        };
        return ResponseEntity.status(status).body(ApiResponse.error(status.value(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unhandled exception: {} - {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "An unexpected error occurred. Please try again."));
    }
}
