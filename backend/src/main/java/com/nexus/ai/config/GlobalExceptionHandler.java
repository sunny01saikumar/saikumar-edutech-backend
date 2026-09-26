package com.nexus.ai.config;

import com.nexus.ai.provider.AIProviderUnavailableException;
import com.nexus.ai.provider.QuotaExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<Map<String, Object>> handleQuotaExceeded(QuotaExceededException ex) {
        Map<String, Object> body = Map.of(
                "error", "TOKEN_QUOTA_EXCEEDED",
                "userId", ex.getUserId() != null ? ex.getUserId() : "",
                "message", ex.getMessage(),
                "status", HttpStatus.TOO_MANY_REQUESTS.value()
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
    }

    @ExceptionHandler(AIProviderUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleProviderUnavailable(AIProviderUnavailableException ex) {
        Map<String, Object> body = Map.of(
                "error", "AI_PROVIDER_UNAVAILABLE",
                "provider", ex.getProviderName(),
                "message", ex.getMessage(),
                "status", HttpStatus.SERVICE_UNAVAILABLE.value()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        Map<String, Object> body = Map.of(
                "error", "INTERNAL_SERVER_ERROR",
                "message", ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred",
                "status", HttpStatus.INTERNAL_SERVER_ERROR.value()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
