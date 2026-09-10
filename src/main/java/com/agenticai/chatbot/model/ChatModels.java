package com.agenticai.chatbot.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * All request/response records for the chatbot API.
 *
 * <p>These are the inner {@code data} payloads wrapped by
 * {@link ApiResponse} in every HTTP response.
 */
public final class ChatModels {

    private ChatModels() {}

    // ── REST request models ────────────────────────────────────────────────

    /**
     * Standard chat request.
     *
     * @param sessionId Optional conversation ID (UUID). If omitted, a new session is created.
     * @param message   The user's message (1–4000 chars, must not be blank).
     * @param streaming Whether to stream the response as SSE tokens.
     */
    public record ChatRequest(

            @Pattern(regexp = "^[a-zA-Z0-9\\-]*$",
                    message = "sessionId must be alphanumeric with optional hyphens")
            @JsonProperty("sessionId")
            String sessionId,

            @NotBlank(message = "message must not be blank")
            @Size(min = 1, max = 4000, message = "message must be between 1 and 4000 characters")
            @JsonProperty("message")
            String message,

            @JsonProperty("streaming")
            boolean streaming
    ) {}

    // ── REST response payloads (wrapped in ApiResponse<T>) ────────────────

    /** Successful chat reply — returned as {@code ApiResponse<ChatReply>}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatReply(
            @JsonProperty("sessionId")  String       sessionId,
            @JsonProperty("reply")      String       reply,
            @JsonProperty("toolsUsed")  List<String> toolsUsed,
            @JsonProperty("model")      String       model,
            @JsonProperty("timestamp")  Instant      timestamp
    ) {
        public static ChatReply of(String sessionId, String reply,
                                   List<String> toolsUsed, String model) {
            return new ChatReply(sessionId, reply, toolsUsed, model, Instant.now());
        }
    }

    /** Returned when a conversation history is cleared — DELETE /api/chat/sessions/{id}. */
    public record SessionClearResponse(
            @JsonProperty("sessionId") String  sessionId,
            @JsonProperty("cleared")   boolean cleared,
            @JsonProperty("message")   String  message,
            @JsonProperty("timestamp") Instant timestamp
    ) {
        public static SessionClearResponse of(String sessionId) {
            return new SessionClearResponse(sessionId, true,
                    "Conversation history cleared for session: " + sessionId,
                    Instant.now());
        }
    }

    /** Response for GET /api/chat/health */
    public record HealthResponse(
            @JsonProperty("status")    String  status,
            @JsonProperty("service")   String  service,
            @JsonProperty("version")   String  version,
            @JsonProperty("timestamp") Instant timestamp
    ) {
        public static HealthResponse up() {
            return new HealthResponse("UP", "spring-ai-chatbot", "1.0.0", Instant.now());
        }
    }

    // ── Tool domain models ─────────────────────────────────────────────────

    public record WeatherResponse(
            @JsonProperty("city")        String city,
            @JsonProperty("temperature") double temperature,
            @JsonProperty("unit")        String unit,
            @JsonProperty("condition")   String condition,
            @JsonProperty("humidity")    int    humidity,
            @JsonProperty("windSpeed")   double windSpeed
    ) {}

    public record OrderTrackingResponse(
            @JsonProperty("orderId")           String orderId,
            @JsonProperty("status")            String status,
            @JsonProperty("estimatedDelivery") String estimatedDelivery,
            @JsonProperty("carrier")           String carrier,
            @JsonProperty("trackingNumber")    String trackingNumber,
            @JsonProperty("lastLocation")      String lastLocation
    ) {}

    public record CalculatorResponse(
            @JsonProperty("expression") String expression,
            @JsonProperty("result")     double result
    ) {}

    public record ProductInfoResponse(
            @JsonProperty("productId")   String  productId,
            @JsonProperty("name")        String  name,
            @JsonProperty("price")       double  price,
            @JsonProperty("inStock")     boolean inStock,
            @JsonProperty("description") String  description,
            @JsonProperty("category")    String  category
    ) {}
}