package com.agenticai.chatbot.controller;

import com.agenticai.chatbot.model.ApiResponse;
import com.agenticai.chatbot.model.ChatModels.*;
import com.agenticai.chatbot.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.net.URI;

/**
 * REST controller for the Spring AI Agentic Chatbot.
 *
 * <p>All endpoints require JWT authentication (ROLE_USER or ROLE_ADMIN)
 * except the health check, which is public.
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chat",
        description = "Agentic AI chat endpoints — send messages to Claude with autonomous tool use. "
                + "Requires JWT Bearer token with ROLE_USER or ROLE_ADMIN.")
@SecurityRequirement(name = "BearerAuth")
public class ChatController {

    private final ChatService chatService;

    // ── 200 OK — Standard Chat ────────────────────────────────────────────

    @Operation(
            summary     = "Send a message to the AI agent",
            description = """
                Sends a user message to Claude. The model autonomously decides which tools
                to call (if any), executes them, and returns a synthesised reply.
                
                Conversation history is maintained per `sessionId` using Spring AI's
                `MessageWindowChatMemory`. Omit `sessionId` to start a new conversation.
                
                **Available tools:**
                - `get_weather`     — "What is the weather in Tokyo?"
                - `track_order`     — "Track my order ORD-1002"
                - `calculate`       — "What is 15% tip on $128.50?"
                - `get_product_info` — "Tell me about PROD-001"
                """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description  = "AI reply returned successfully",
            content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(name = "weather-response", value = """
                    {
                      "success": true,
                      "status": 200,
                      "message": "OK",
                      "data": {
                        "sessionId":  "abc-123",
                        "reply":      "The current weather in London is 15.0°C, Partly Cloudy with 65% humidity.",
                        "toolsUsed":  ["get_weather"],
                        "model":      "claude-sonnet-4-20250514",
                        "timestamp":  "2026-05-31T10:00:00Z"
                      },
                      "path":      "/api/chat",
                      "timestamp": "2026-05-31T10:00:00Z"
                    }
                    """)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "Validation failed (blank message, message > 4000 chars, etc.)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
            description = "Missing or invalid JWT token")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429",
            description = "Anthropic rate limit exceeded — see Retry-After header")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502",
            description = "Upstream Anthropic API error")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "504",
            description = "AI model response timed out")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ChatReply>> chat(
            @Valid @RequestBody ChatRequest request,
            HttpServletRequest httpReq) {

        log.debug("POST /api/chat — session={}", request.sessionId());
        ChatReply reply = chatService.chat(request);
        return ResponseEntity.ok(ApiResponse.ok(reply, httpReq.getRequestURI()));
    }

    // ── 200 OK SSE — Streaming Chat ───────────────────────────────────────

    @Operation(
            summary     = "Stream AI reply as Server-Sent Events (SSE)",
            description = """
                Identical to `POST /api/chat` but streams the response token-by-token
                as SSE (`text/event-stream`). Creates a real-time typing effect in the UI.
                
                Each SSE event contains a plain-text token in the `data:` field.
                The stream ends when the model finishes generating.
                """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description  = "SSE stream started (tokens arrive asynchronously)",
            content = @Content(mediaType = "text/event-stream",
                    examples = @ExampleObject(value = "data: The\ndata:  weather\ndata:  in London\ndata:  is 15°C.")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
            description  = "Missing or invalid JWT token")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @PostMapping(
            value    = "/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@Valid @RequestBody ChatRequest request) {
        log.debug("POST /api/chat/stream — session={}", request.sessionId());
        return chatService.stream(request);
    }

    // ── 200 OK — Health ───────────────────────────────────────────────────

    @Operation(
            summary     = "Health check",
            description = "Returns application status. **Public endpoint — no JWT required.**",
            security    = {}    // override global security requirement — public
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "Application is UP",
            content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(value = """
                    {
                      "success": true,
                      "status": 200,
                      "data": { "status": "UP", "service": "spring-ai-chatbot", "version": "1.0.0" }
                    }
                    """)))
    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<HealthResponse>> health(HttpServletRequest httpReq) {
        return ResponseEntity.ok(ApiResponse.ok(HealthResponse.up(), httpReq.getRequestURI()));
    }

    // ── 204 No Content — Clear Session ───────────────────────────────────

    @Operation(
            summary     = "Clear conversation history for a session",
            description = "Deletes all stored messages for the given `sessionId`. Returns 204 with empty body.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204",
            description = "Session history cleared — empty body")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "Invalid sessionId format")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
            description = "Not authenticated")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @DeleteMapping(value = "/sessions/{sessionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> clearSession(
            @Parameter(description = "Session ID to clear (alphanumeric with hyphens)", example = "abc-123")
            @PathVariable
            @NotBlank
            @Pattern(regexp = "^[a-zA-Z0-9\\-]+$")
            String sessionId,
            HttpServletRequest httpReq) {

        log.info("DELETE /api/chat/sessions/{}", sessionId);
        chatService.clearSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    // ── 301 Redirect ──────────────────────────────────────────────────────

    @Operation(
            summary  = "Redirect to health",
            description = "Permanently redirects bare GET /api/chat to /api/chat/health.",
            security = {}
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "301",
            description = "Moved Permanently → /api/chat/health")
    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Void> redirectToHealth() {
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .location(URI.create("/api/chat/health"))
                .build();
    }

    // ── 302 Redirect ──────────────────────────────────────────────────────

    @Operation(summary = "Temporary redirect for /info path", security = {})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "302",
            description = "Found → /api/chat/health")
    @GetMapping(value = "/info", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> redirectInfo() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/api/chat/health"))
                .build();
    }
}