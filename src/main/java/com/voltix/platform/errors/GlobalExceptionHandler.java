package com.voltix.platform.errors;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.util.concurrent.RejectedExecutionException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Request validation failed");
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler({DataAccessException.class, org.springframework.transaction.TransactionException.class})
    ResponseEntity<ApiError> handleDataAccess(Exception ex) {
        log.error("=== DATA ACCESS EXCEPTION ===");
        log.error("Exception class: {}", ex.getClass().getName());
        log.error("Exception message: {}", ex.getMessage());

        // Full cause chain
        Throwable cause = ex;
        int depth = 0;
        while (cause != null) {
            log.error("  Cause[{}]: class={}, message={}", depth, cause.getClass().getName(), cause.getMessage());
            if (cause instanceof SQLException sqlEx) {
                log.error("  SQLException: SQLState={}, errorCode={}", sqlEx.getSQLState(), sqlEx.getErrorCode());
                SQLException next = sqlEx.getNextException();
                int nextDepth = 0;
                while (next != null) {
log.error("  NextException[{}]: SQLState={}, errorCode={}, message={}",
                        nextDepth, next.getSQLState(), next.getErrorCode(), next.getMessage());
                    next = next.getNextException();
                    nextDepth++;
                }
            }
            cause = cause.getCause();
            depth++;
        }

        // Stack trace
log.error("Stack trace:", ex);

        // Special handling for BadSqlGrammarException
        if (ex instanceof BadSqlGrammarException badSql) {
            log.error("BadSqlGrammarException: sql={}", badSql.getSql());
        }

        log.error("=== END DATA ACCESS EXCEPTION ===");
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Telemetry durability store is unavailable");
    }

    @ExceptionHandler(RejectedExecutionException.class)
    ResponseEntity<ApiError> handleRejected(RejectedExecutionException ex) {
        log.warn("Ingestion task rejected: {}", ex.getMessage());
        return error(HttpStatus.TOO_MANY_REQUESTS, "VoltiX ingestion capacity is saturated");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), status.getReasonPhrase(), message));
    }
}
