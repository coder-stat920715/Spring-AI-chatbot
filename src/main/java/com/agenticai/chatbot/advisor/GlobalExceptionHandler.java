package com.agenticai.chatbot.advisor;

import com.agenticai.chatbot.model.ApiResponse;
import com.agenticai.chatbot.model.ChatException.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global REST exception handler.
 *
 * <p>Converts every typed exception (and Spring MVC's built-in ones)
 * into a consistent {@link ApiResponse} JSON body with the correct HTTP status.
 *
 * <pre>
 * Exception Type                      → HTTP Status
 * ────────────────────────────────────────────────────────
 * MethodArgumentNotValidException     → 400 Bad Request
 * HttpMessageNotReadableException     → 400 Bad Request  (malformed JSON)
 * BadRequestException                 → 400 Bad Request
 * NoHandlerFoundException             → 404 Not Found
 * HttpRequestMethodNotSupportedException → 405 Method Not Allowed
 * HttpMediaTypeNotSupportedException  → 415 Unsupported Media Type
 * UnprocessableException              → 422 Unprocessable Entity
 * RateLimitException                  → 429 Too Many Requests
 * AiApiException                      → 502 Bad Gateway
 * ServiceUnavailableException         → 503 Service Unavailable
 * AiTimeoutException                  → 504 Gateway Timeout
 * ChatBaseException                   → 500 Internal Server Error
 * Exception (catch-all)               → 500 Internal Server Error
 * </pre>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 400 Bad Request ────────────────────────────────────────────────────

    /** Bean validation failed (e.g. @NotBlank, @Size). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {

        Map<String, String> fieldErrors = ex.getBindingResult().getAllErrors().stream()
                .filter(e -> e instanceof FieldError)
                .map(e -> (FieldError) e)
                .collect(Collectors.toMap(FieldError::getField,
                        e -> e.getDefaultMessage() != null
                                ? e.getDefaultMessage() : "invalid value"));

        log.warn("Validation failed on {}: {}", req.getRequestURI(), fieldErrors);

        var body = new ApiResponse<>(
                false, 400, "Validation Failed",
                fieldErrors,                               // field → error message map in data
                new ApiResponse.ApiError("VALIDATION_ERROR",
                        "One or more request fields failed validation."),
                req.getRequestURI(), java.time.Instant.now());

        return ResponseEntity.badRequest().body(body);
    }

    /** Malformed JSON body (cannot deserialise). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedJson(
            HttpMessageNotReadableException ex, HttpServletRequest req) {

        log.warn("Malformed JSON on {}: {}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.badRequest(
                        "Request body is malformed or missing. Ensure Content-Type is application/json "
                                + "and the body is valid JSON.",
                        req.getRequestURI()));
    }

    /** Service-layer semantic 400. */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(
            BadRequestException ex, HttpServletRequest req) {

        log.warn("Bad request on {}: {}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.badRequest(ex.getMessage(), req.getRequestURI()));
    }

    // ── 404 Not Found ─────────────────────────────────────────────────────

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(
            NoHandlerFoundException ex, HttpServletRequest req) {

        log.warn("No handler found: {} {}", ex.getHttpMethod(), req.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.notFound(
                        "No endpoint found for " + ex.getHttpMethod() + " " + req.getRequestURI(),
                        req.getRequestURI()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest req) {

        log.warn("Resource not found on {}: {}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.notFound(ex.getMessage(), req.getRequestURI()));
    }

    // ── 405 Method Not Allowed ─────────────────────────────────────────────

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {

        String detail = ex.getMethod() + " is not supported on " + req.getRequestURI()
                + ". Supported methods: " + ex.getSupportedHttpMethods();
        log.warn("Method not allowed: {}", detail);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.methodNotAllowed(detail, req.getRequestURI()));
    }

    // ── 415 Unsupported Media Type ─────────────────────────────────────────

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {

        String detail = "Content-Type '" + ex.getContentType() + "' is not supported. "
                + "Use 'application/json'.";
        log.warn("Unsupported media type on {}: {}", req.getRequestURI(), detail);
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiResponse<>(false, 415, "Unsupported Media Type",
                        null,
                        new ApiResponse.ApiError("UNSUPPORTED_MEDIA_TYPE", detail),
                        req.getRequestURI(), java.time.Instant.now()));
    }

    // ── 422 Unprocessable Entity ───────────────────────────────────────────

    @ExceptionHandler(UnprocessableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnprocessable(
            UnprocessableException ex, HttpServletRequest req) {

        log.warn("Unprocessable entity on {}: {}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.unprocessable(ex.getMessage(), req.getRequestURI()));
    }

    // ── 429 Too Many Requests ──────────────────────────────────────────────

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimit(
            RateLimitException ex, HttpServletRequest req) {

        log.warn("Rate limit on {}: {}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "60")
                .body(ApiResponse.tooManyRequests(ex.getMessage(), req.getRequestURI()));
    }

    // ── 500 Internal Server Error ──────────────────────────────────────────

    @ExceptionHandler(ChatBaseException.class)
    public ResponseEntity<ApiResponse<Void>> handleChatBase(
            ChatBaseException ex, HttpServletRequest req) {

        log.error("Chat error on {}: {}", req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.internalError(
                        "An internal error occurred. Please try again.",
                        req.getRequestURI()));
    }

    // ── 502 Bad Gateway ────────────────────────────────────────────────────

    @ExceptionHandler(AiApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiApi(
            AiApiException ex, HttpServletRequest req) {

        log.error("AI API error on {}: {}", req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.badGateway(ex.getMessage(), req.getRequestURI()));
    }

    // ── 503 Service Unavailable ────────────────────────────────────────────

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleServiceUnavailable(
            ServiceUnavailableException ex, HttpServletRequest req) {

        log.error("Service unavailable on {}: {}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "30")
                .body(ApiResponse.serviceUnavailable(ex.getMessage(), req.getRequestURI()));
    }

    // ── 504 Gateway Timeout ────────────────────────────────────────────────

    @ExceptionHandler(AiTimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiTimeout(
            AiTimeoutException ex, HttpServletRequest req) {

        log.error("AI timeout on {}: {}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(ApiResponse.gatewayTimeout(ex.getMessage(), req.getRequestURI()));
    }

    // ── Catch-all 500 ─────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(
            Exception ex, HttpServletRequest req) {

        log.error("Unhandled exception on {}: {}", req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.internalError(
                        "An unexpected error occurred. Please try again.",
                        req.getRequestURI()));
    }
}