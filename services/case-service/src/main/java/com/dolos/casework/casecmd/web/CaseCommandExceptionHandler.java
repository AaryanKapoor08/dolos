package com.dolos.casework.casecmd.web;

import com.dolos.common.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the command side's domain/Axon failures to the shared {@link ApiError} envelope:
 *
 * <ul>
 *   <li>{@link AggregateNotFoundException} (a command for a case id that has no event stream) → 404;
 *   <li>{@link IllegalStateException} (an invariant violation, e.g. acting on a closed case) → 409.
 * </ul>
 *
 * <p>Validation and {@code IllegalArgumentException} stay with the application-wide handler.
 */
@RestControllerAdvice
public class CaseCommandExceptionHandler {

    @ExceptionHandler(AggregateNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            AggregateNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "case not found", request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleConflict(
            IllegalStateException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status, String message, HttpServletRequest request) {
        ApiError body =
                ApiError.of(status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
