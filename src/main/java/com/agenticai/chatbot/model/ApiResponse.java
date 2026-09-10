package com.agenticai.chatbot.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Standard API response envelope used across all endpoints.
 *
 * <p>Every HTTP response — success or error — is wrapped in this class
 * so clients always receive a consistent JSON structure:
 *
 * <pre>
 * {
 *   "success": true,
 *   "status":  200,
 *   "message": "OK",
 *   "data":    { ... },        // present on 2xx
 *   "error":   null,           // present on 4xx / 5xx
 *   "path":    "/api/chat",
 *   "timestamp": "2026-05-31T..."
 * }
 * </pre>
 *
 * @param <T> The type of the payload in {@code data}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(

        @JsonProperty("success")   boolean success,
        @JsonProperty("status")    int     status,
        @JsonProperty("message")   String  message,
        @JsonProperty("data")      T       data,
        @JsonProperty("error")     ApiError error,
        @JsonProperty("path")      String  path,
        @JsonProperty("timestamp") Instant timestamp
) {
    // ── 2xx factories ─────────────────────────────────────────────────────

    /** 200 OK */
    public static <T> ApiResponse<T> ok(T data, String path) {
        return new ApiResponse<>(true, 200, "OK", data, null, path, Instant.now());
    }

    /** 201 Created */
    public static <T> ApiResponse<T> created(T data, String path) {
        return new ApiResponse<>(true, 201, "Created", data, null, path, Instant.now());
    }

    /** 204 No Content (data is null by design) */
    public static <T> ApiResponse<T> noContent(String path) {
        return new ApiResponse<>(true, 204, "No Content", null, null, path, Instant.now());
    }

    // ── 4xx factories ─────────────────────────────────────────────────────

    /** 400 Bad Request */
    public static <T> ApiResponse<T> badRequest(String detail, String path) {
        return new ApiResponse<>(false, 400, "Bad Request",
                null, new ApiError("BAD_REQUEST", detail), path, Instant.now());
    }

    /** 401 Unauthorized */
    public static <T> ApiResponse<T> unauthorized(String detail, String path) {
        return new ApiResponse<>(false, 401, "Unauthorized",
                null, new ApiError("UNAUTHORIZED", detail), path, Instant.now());
    }

    /** 404 Not Found */
    public static <T> ApiResponse<T> notFound(String detail, String path) {
        return new ApiResponse<>(false, 404, "Not Found",
                null, new ApiError("NOT_FOUND", detail), path, Instant.now());
    }

    /** 405 Method Not Allowed */
    public static <T> ApiResponse<T> methodNotAllowed(String detail, String path) {
        return new ApiResponse<>(false, 405, "Method Not Allowed",
                null, new ApiError("METHOD_NOT_ALLOWED", detail), path, Instant.now());
    }

    /** 409 Conflict */
    public static <T> ApiResponse<T> conflict(String detail, String path) {
        return new ApiResponse<>(false, 409, "Conflict",
                null, new ApiError("CONFLICT", detail), path, Instant.now());
    }

    /** 422 Unprocessable Entity (validation passed but business rules failed) */
    public static <T> ApiResponse<T> unprocessable(String detail, String path) {
        return new ApiResponse<>(false, 422, "Unprocessable Entity",
                null, new ApiError("UNPROCESSABLE_ENTITY", detail), path, Instant.now());
    }

    /** 429 Too Many Requests */
    public static <T> ApiResponse<T> tooManyRequests(String detail, String path) {
        return new ApiResponse<>(false, 429, "Too Many Requests",
                null, new ApiError("RATE_LIMITED", detail), path, Instant.now());
    }

    // ── 5xx factories ─────────────────────────────────────────────────────

    /** 500 Internal Server Error */
    public static <T> ApiResponse<T> internalError(String detail, String path) {
        return new ApiResponse<>(false, 500, "Internal Server Error",
                null, new ApiError("INTERNAL_ERROR", detail), path, Instant.now());
    }

    /** 502 Bad Gateway (upstream AI API error) */
    public static <T> ApiResponse<T> badGateway(String detail, String path) {
        return new ApiResponse<>(false, 502, "Bad Gateway",
                null, new ApiError("AI_API_ERROR", detail), path, Instant.now());
    }

    /** 503 Service Unavailable */
    public static <T> ApiResponse<T> serviceUnavailable(String detail, String path) {
        return new ApiResponse<>(false, 503, "Service Unavailable",
                null, new ApiError("SERVICE_UNAVAILABLE", detail), path, Instant.now());
    }

    /** 504 Gateway Timeout */
    public static <T> ApiResponse<T> gatewayTimeout(String detail, String path) {
        return new ApiResponse<>(false, 504, "Gateway Timeout",
                null, new ApiError("GATEWAY_TIMEOUT", detail), path, Instant.now());
    }

    // ── Nested error detail ────────────────────────────────────────────────

    public record ApiError(
            @JsonProperty("code")   String code,
            @JsonProperty("detail") String detail
    ) {}
}
