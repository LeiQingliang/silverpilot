package com.cecsmsserve.util;

import com.cecsmsserve.service.AiResourceGuard;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Order(-10)
@RestControllerAdvice
public class AiProtectionExceptionHandler {
    @ExceptionHandler(AiResourceGuard.Rejected.class)
    public ResponseEntity<?> rejected(AiResourceGuard.Rejected ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .header("Retry-After", Long.toString(ex.retryAfterSeconds()))
                .header("Cache-Control", "no-store")
                .body(Map.of("success", false, "code", ex.getHttpStatus(),
                        "error", ex.getMessage(), "msg", ex.getMessage()));
    }
}
