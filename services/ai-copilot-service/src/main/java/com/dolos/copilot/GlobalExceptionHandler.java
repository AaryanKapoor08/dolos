package com.dolos.copilot;

import com.dolos.common.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

/**
 * Maps exceptions to the shared {@link ApiError} envelope so clients get a consistent error shape,
 * matching the other Dolos services. Lives in the application root package so it applies platform-wide.
 *
 * <p>A failed call to the Ollama model (e.g. the model server is down or still pulling) surfaces as a
 * {@link RestClientException} from Spring AI's underlying HTTP client; we map it to 502 Bad Gateway so
 * the copilot reports an upstream problem rather than leaking a stack trace.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(e -> e.getField() + " " + e.getDefaultMessage())
                        .findFirst()
                        .orElse("validation failed");
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiError> handleModelUnavailable(
            RestClientException ex, HttpServletRequest request) {
        log.warn("Ollama model call failed: {}", ex.getMessage());
        return build(
                HttpStatus.BAD_GATEWAY,
                "the language model is unavailable; is Ollama up and the model pulled?",
                request);
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status, String message, HttpServletRequest request) {
        ApiError body =
                ApiError.of(status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
