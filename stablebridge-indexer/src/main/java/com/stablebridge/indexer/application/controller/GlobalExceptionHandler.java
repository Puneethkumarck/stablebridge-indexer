package com.stablebridge.indexer.application.controller;

import com.stablebridge.indexer.api.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Global exception handler for the REST API.
 *
 * <p>Converts exceptions to {@link ErrorResponse} DTOs with appropriate HTTP status codes.
 * All responses include a timestamp for audit trail purposes.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles validation errors from {@code @Valid} annotated request bodies.
     *
     * @param ex the validation exception
     * @return error response with 400 status
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation error — {}", message);
        return new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                message,
                Instant.now());
    }

    /**
     * Handles missing required query parameters.
     *
     * @param ex the missing parameter exception
     * @return error response with 400 status
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMissingParameter(MissingServletRequestParameterException ex) {
        log.warn("Missing required parameter — {}", ex.getMessage());
        return new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage(),
                Instant.now());
    }

    /**
     * Handles type mismatch errors (e.g., invalid enum values).
     *
     * @param ex the type mismatch exception
     * @return error response with 400 status
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value for parameter '" + ex.getName() + "': " + ex.getValue();
        log.warn("Type mismatch — {}", message);
        return new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                message,
                Instant.now());
    }

    /**
     * Handles database constraint violations (e.g., duplicate address + networkType).
     *
     * @param ex the data integrity violation exception
     * @return error response with 409 status
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation — {}", ex.getMessage());
        return new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                "Conflict",
                "A wallet address with the same address and network type already exists",
                Instant.now());
    }

    /**
     * Handles all unhandled exceptions as internal server errors.
     *
     * @param ex the unhandled exception
     * @return error response with 500 status
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        return new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected error occurred",
                Instant.now());
    }
}
