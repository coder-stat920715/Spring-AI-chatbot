package com.agenticai.chatbot.model;

/**
 * Typed exception hierarchy for the chatbot application.
 *
 * <p>Each exception maps to a specific HTTP status code, allowing
 * {@link com.agenticai.chatbot.advisor.GlobalExceptionHandler} to return
 * precise, machine-readable error responses.
 *
 * <pre>
 * Exception                   → HTTP Status
 * ────────────────────────────────────────────
 * ChatException (base)        → 500
 *   BadRequestException       → 400
 *   ResourceNotFoundException → 404
 *   RateLimitException        → 429
 *   AiApiException            → 502
 *   AiTimeoutException        → 504
 *   ServiceUnavailableException → 503
 * </pre>
 */
public class ChatException {

    /** Base exception — maps to 500 Internal Server Error. */
    public static class ChatBaseException extends RuntimeException {
        public ChatBaseException(String message) { super(message); }
        public ChatBaseException(String message, Throwable cause) { super(message, cause); }
    }

    /** 400 Bad Request — malformed input that passes bean validation but fails business logic. */
    public static class BadRequestException extends ChatBaseException {
        public BadRequestException(String message) { super(message); }
    }

    /** 404 Not Found — requested resource does not exist (e.g., unknown session). */
    public static class ResourceNotFoundException extends ChatBaseException {
        public ResourceNotFoundException(String resource, String id) {
            super(resource + " not found: " + id);
        }
    }

    /** 422 Unprocessable Entity — input valid, but business rule rejected it. */
    public static class UnprocessableException extends ChatBaseException {
        public UnprocessableException(String message) { super(message); }
    }

    /** 429 Too Many Requests — rate limit exceeded. */
    public static class RateLimitException extends ChatBaseException {
        public RateLimitException(String message) { super(message); }
        public RateLimitException() { super("Rate limit exceeded. Please wait before sending another message."); }
    }

    /** 502 Bad Gateway — upstream Anthropic API returned an error. */
    public static class AiApiException extends ChatBaseException {
        public AiApiException(String message) { super(message); }
        public AiApiException(String message, Throwable cause) { super(message, cause); }
    }

    /** 503 Service Unavailable — AI model or a dependent service is temporarily down. */
    public static class ServiceUnavailableException extends ChatBaseException {
        public ServiceUnavailableException(String message) { super(message); }
    }

    /** 504 Gateway Timeout — upstream AI API did not respond in time. */
    public static class AiTimeoutException extends ChatBaseException {
        public AiTimeoutException(String message) { super(message); }
        public AiTimeoutException() { super("AI model timed out. Please try again."); }
    }
}
