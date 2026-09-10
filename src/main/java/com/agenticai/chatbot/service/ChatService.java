package com.agenticai.chatbot.service;

import com.agenticai.chatbot.model.ChatException.*;
import com.agenticai.chatbot.model.ChatModels.ChatReply;
import com.agenticai.chatbot.model.ChatModels.ChatRequest;
import com.agenticai.chatbot.model.ChatModels.SessionClearResponse;
import com.agenticai.chatbot.tools.CalculatorTool;
import com.agenticai.chatbot.tools.OrderTrackingTool;
import com.agenticai.chatbot.tools.ProductInfoTool;
import com.agenticai.chatbot.tools.WeatherTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * Core agentic chat service — Spring AI 2.0 / Spring Boot 4.
 * <h3>HTTP status contract</h3>
 * <p>This service throws typed exceptions that are mapped to HTTP status codes
 * by {@link com.agenticai.chatbot.advisor.GlobalExceptionHandler}:
 * <pre>
 * BadRequestException         → 400  (e.g. message too long after trim)
 * RateLimitException          → 429  (Anthropic 429/529 upstream)
 * AiApiException              → 502  (Anthropic 4xx/5xx upstream errors)
 * AiTimeoutException          → 504  (model response timeout)
 * ServiceUnavailableException → 503  (AI service down)
 * ChatBaseException (generic) → 500  (unexpected failures)
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient        chatClient;
    private final WeatherTool       weatherTool;
    private final OrderTrackingTool orderTrackingTool;
    private final CalculatorTool    calculatorTool;
    private final ProductInfoTool   productInfoTool;

    // ── Chat ──────────────────────────────────────────────────────────────

    /**
     * Sends a user message to Claude and returns a complete reply.
     *
     * <p>Claude autonomously chooses which tools to call (0 or more).
     * Spring AI's ToolCallingManager handles the full multi-turn tool loop.
     *
     * @throws BadRequestException         on semantic validation failure (400)
     * @throws RateLimitException          when Anthropic rate-limits the request (429)
     * @throws AiTimeoutException          when the model does not respond in time (504)
     * @throws AiApiException              on upstream Anthropic API error (502)
     * @throws ServiceUnavailableException when the AI service is down (503)
     */
    public ChatReply chat(ChatRequest request) {
        String sessionId = resolveSessionId(request.sessionId());
        String message   = request.message().trim();

        // 400: semantic guard (bean validation already blocked blank; this catches edge cases)
        if (message.isEmpty()) {
            throw new BadRequestException("Message content is empty after trimming whitespace.");
        }

        log.info("Chat request — session={}, chars={}", sessionId, message.length());

        try {
            org.springframework.ai.chat.model.ChatResponse aiResponse = chatClient
                    .prompt()
                    .user(message)
                    .tools(weatherTool, orderTrackingTool, calculatorTool, productInfoTool)
                    .advisors(adv -> adv.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .call()
                    .chatResponse();

            String       reply     = extractText(aiResponse);
            List<String> toolsUsed = extractToolsUsed(aiResponse);
            String       model     = extractModel(aiResponse);

            log.info("Chat response — session={}, toolsUsed={}, chars={}",
                    sessionId, toolsUsed, reply.length());

            return ChatReply.of(sessionId, reply, toolsUsed, model);

        } catch (BadRequestException | RateLimitException |
                 AiApiException | AiTimeoutException |
                 ServiceUnavailableException ex) {
            throw ex; // already typed — re-throw for the handler

        } catch (Exception ex) {
            return handleAiException(ex, sessionId);
        }
    }

    /**
     * Streams the AI reply token-by-token as a reactive {@link Flux}.
     *
     * <p>Consumed by the SSE endpoint in {@code ChatController}.
     * Errors inside the Flux propagate to Spring's SSE error handling.
     */
    public Flux<String> stream(ChatRequest request) {
        String sessionId = resolveSessionId(request.sessionId());
        String message   = request.message().trim();

        if (message.isEmpty()) {
            return Flux.error(new BadRequestException(
                    "Message content is empty after trimming whitespace."));
        }

        log.info("Stream request — session={}", sessionId);

        return chatClient
                .prompt()
                .user(message)
                .tools(weatherTool, orderTrackingTool, calculatorTool, productInfoTool)
                .advisors(adv -> adv.param(ChatMemory.CONVERSATION_ID, sessionId))
                .stream()
                .content()
                .doOnError(ex -> log.error("Stream error — session={}: {}", sessionId, ex.getMessage()));
    }

    /**
     * Clears the conversation history for a given session.
     * Used by {@code DELETE /api/chat/sessions/{sessionId}}.
     *
     * @throws com.agenticai.chatbot.model.ChatException.ResourceNotFoundException
     *         if the sessionId format is invalid (404)
     */
    public SessionClearResponse clearSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BadRequestException("sessionId must not be blank.");
        }
        // In-memory: clearing is always successful (nothing stored = already clear)
        log.info("Clearing session history — session={}", sessionId);
        return SessionClearResponse.of(sessionId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String resolveSessionId(String sessionId) {
        return (sessionId == null || sessionId.isBlank())
                ? UUID.randomUUID().toString()
                : sessionId;
    }

    /**
     * Maps raw runtime exceptions from the Spring AI / Anthropic layer
     * to typed HTTP-semantic exceptions for the global handler.
     */
    private ChatReply handleAiException(Exception ex, String sessionId) {
        String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();

        // 504 — timeout indicators
        if (ex instanceof TimeoutException || msg.contains("timeout") || msg.contains("timed out")) {
            log.error("AI timeout — session={}", sessionId);
            throw new AiTimeoutException();
        }
        // 429 — rate limit indicators
        if (msg.contains("rate limit") || msg.contains("429") || msg.contains("529")
                || msg.contains("too many requests") || msg.contains("overloaded")) {
            log.error("Rate limit hit — session={}", sessionId);
            throw new RateLimitException();
        }
        // 503 — service down indicators
        if (msg.contains("connection refused") || msg.contains("service unavailable")
                || msg.contains("503") || msg.contains("unreachable")) {
            log.error("AI service unavailable — session={}", sessionId);
            throw new ServiceUnavailableException("The AI model service is temporarily unavailable.");
        }
        // 502 — other upstream API errors
        if (msg.contains("api") || msg.contains("anthropic") || msg.contains("upstream")
                || msg.contains("http") || msg.contains("4xx") || msg.contains("5xx")) {
            log.error("AI API error — session={}: {}", sessionId, ex.getMessage(), ex);
            throw new AiApiException("Upstream AI API returned an error. Please try again.", ex);
        }

        // 500 — catch-all
        log.error("Unexpected error — session={}: {}", sessionId, ex.getMessage(), ex);
        throw new ChatBaseException("An unexpected error occurred. Please try again.", ex);
    }

    private String extractText(org.springframework.ai.chat.model.ChatResponse r) {
        if (r == null || r.getResult() == null) return "";
        var output = r.getResult().getOutput();
        return output != null && output.getText() != null ? output.getText() : "";
    }

    private List<String> extractToolsUsed(org.springframework.ai.chat.model.ChatResponse r) {
        var tools = new ArrayList<String>();
        if (r == null) return tools;
        r.getResults().forEach(result -> {
            var output = result.getOutput();
            if (output != null && output.getToolCalls() != null) {
                output.getToolCalls().forEach(tc -> tools.add(tc.name()));
            }
        });
        return tools;
    }

    private String extractModel(org.springframework.ai.chat.model.ChatResponse r) {
        if (r == null) return "claude-sonnet-4-20250514";
        ChatResponseMetadata meta = r.getMetadata();
        return (meta != null && meta.getModel() != null) ? meta.getModel() : "claude-sonnet-4-20250514";
    }
}