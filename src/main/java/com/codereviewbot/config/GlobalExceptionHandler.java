package com.codereviewbot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Catches all unhandled exceptions and returns clean JSON error responses.
 *
 * SECURITY: Never expose internal stack traces or error messages to the client.
 * Stack traces can reveal:
 *  - Internal class names and file paths
 *  - Library versions (useful for known-CVE attacks)
 *  - Database schema details
 *
 * We log the full error internally, but return only a generic message externally.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Catches all unhandled RuntimeExceptions */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        // Log full details internally — never send to client
        log.error("Unhandled RuntimeException: {}", e.getMessage(), e);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of(
                "error", "An internal error occurred",
                "timestamp", LocalDateTime.now().toString()
                // Deliberately NOT including e.getMessage() — security
            ));
    }

    /** Catches all other unhandled exceptions */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception e) {
        log.error("Unhandled Exception: {}", e.getMessage(), e);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of(
                "error", "An unexpected error occurred",
                "timestamp", LocalDateTime.now().toString()
            ));
    }

    /** Handles illegal arguments gracefully */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("IllegalArgumentException: {}", e.getMessage());

        return ResponseEntity.badRequest()
            .body(Map.of(
                "error", "Invalid request",
                "timestamp", LocalDateTime.now().toString()
            ));
    }
}
