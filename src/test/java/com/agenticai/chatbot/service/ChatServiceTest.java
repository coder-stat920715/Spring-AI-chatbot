package com.agenticai.chatbot.service;

import com.agenticai.chatbot.model.ChatException.*;
import com.agenticai.chatbot.model.ChatModels.ChatReply;
import com.agenticai.chatbot.model.ChatModels.ChatRequest;
import com.agenticai.chatbot.model.ChatModels.SessionClearResponse;
import com.agenticai.chatbot.tools.CalculatorTool;
import com.agenticai.chatbot.tools.OrderTrackingTool;
import com.agenticai.chatbot.tools.ProductInfoTool;
import com.agenticai.chatbot.tools.WeatherTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ChatService}.
 *
 * <p>Coverage targets:
 * <ul>
 *   <li>chat()         — happy path, blank message, all 5 exception branches, null response guards</li>
 *   <li>stream()       — happy path, blank message</li>
 *   <li>clearSession() — happy path, blank sessionId</li>
 *   <li>resolveSessionId() — null, blank, and provided values</li>
 *   <li>extractText()  — null response, null result, null output, null text</li>
 *   <li>extractToolsUsed() — null response, null toolCalls, populated toolCalls</li>
 *   <li>extractModel() — null response, null metadata, null model, populated metadata</li>
 *   <li>handleAiException() — all 5 keyword branches (timeout, rate-limit, 503, 502, catch-all)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChatService")
class ChatServiceTest {

    // ── Mocks ─────────────────────────────────────────────────────────────

    @Mock
    private ChatClient chatClient;

    @Mock
    private WeatherTool weatherTool;

    @Mock
    private OrderTrackingTool orderTrackingTool;

    @Mock
    private CalculatorTool calculatorTool;

    @Mock
    private ProductInfoTool productInfoTool;

    // ── Fluent builder chain mocks ─────────────────────────────────────────

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.ChatClientRequestSpec toolsSpec;

    @Mock
    private ChatClient.ChatClientRequestSpec advisorsSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private ChatClient.StreamResponseSpec streamResponseSpec;

    // ── Subject under test ────────────────────────────────────────────────

    @InjectMocks
    private ChatService chatService;

    // ── Test fixtures ─────────────────────────────────────────────────────

    private static final String SESSION_ID = "test-session-001";
    private static final String MESSAGE    = "Hello, world!";

    // ── Setup ─────────────────────────────────────────────────────────────

    @BeforeEach
    void setUpChatClientChain() {
        // Wire the full ChatClient fluent chain for both chat() and stream() paths.
        lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        lenient().when(requestSpec.user(anyString())).thenReturn(toolsSpec);
        lenient().when(toolsSpec.tools(any(WeatherTool.class),
                any(OrderTrackingTool.class),
                any(CalculatorTool.class),
                any(ProductInfoTool.class)))
                .thenReturn(advisorsSpec);
        // advisors() accepts a Consumer<AdvisorsSpec> — use lenient so unused stubs don't fail
        lenient().when(advisorsSpec.advisors(any(Consumer.class))).thenReturn(advisorsSpec);
    }

    // ═════════════════════════════════════════════════════════════════════
    // chat()
    // ═════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("chat()")
    class ChatMethod {

        @Test
        @DisplayName("returns ChatReply with session, reply text, toolsUsed and model on success")
        void happyPath_withToolsAndModel() {
            // Arrange
            AssistantMessage assistantMsg = mock(AssistantMessage.class);
            when(assistantMsg.getText()).thenReturn("The weather in Tokyo is 27°C.");

            // Now returns a real Record item in the list instead of a broken Mockito proxy
            when(assistantMsg.getToolCalls()).thenReturn(List.of(
                    mockToolCall("get_weather")
            ));

            Generation generation = mock(Generation.class);
            when(generation.getOutput()).thenReturn(assistantMsg);

            ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
            when(metadata.getModel()).thenReturn("claude-sonnet-4-20250514");

            ChatResponse aiResponse = mock(ChatResponse.class);
            when(aiResponse.getResult()).thenReturn(generation);
            when(aiResponse.getResults()).thenReturn(List.of(generation));
            when(aiResponse.getMetadata()).thenReturn(metadata);

            when(advisorsSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.chatResponse()).thenReturn(aiResponse);

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);

            // Act
            ChatReply reply = chatService.chat(request);

            // Assert
            assertThat(reply.sessionId()).isEqualTo(SESSION_ID);
            assertThat(reply.reply()).isEqualTo("The weather in Tokyo is 27°C.");
            assertThat(reply.toolsUsed()).containsExactly("get_weather");
            assertThat(reply.model()).isEqualTo("claude-sonnet-4-20250514");
        }

        @Test
        @DisplayName("generates a UUID sessionId when request sessionId is null")
        void nullSessionId_generatesUuid() {
            ChatResponse aiResponse = buildMinimalChatResponse("Hi!");
            when(advisorsSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.chatResponse()).thenReturn(aiResponse);

            ChatRequest request = new ChatRequest(null, MESSAGE, false);
            ChatReply reply = chatService.chat(request);

            assertThat(reply.sessionId())
                    .isNotBlank()
                    .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("generates a UUID sessionId when request sessionId is blank")
        void blankSessionId_generatesUuid() {
            ChatResponse aiResponse = buildMinimalChatResponse("Hi!");
            when(advisorsSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.chatResponse()).thenReturn(aiResponse);

            ChatRequest request = new ChatRequest("   ", MESSAGE, false);
            ChatReply reply = chatService.chat(request);

            assertThat(reply.sessionId()).isNotBlank();
        }

        @Test
        @DisplayName("throws BadRequestException when message is whitespace-only after trim")
        void whitespaceMessage_throwsBadRequest() {
            ChatRequest request = new ChatRequest(SESSION_ID, "   ", false);

            assertThatThrownBy(() -> chatService.chat(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("empty after trimming");
        }

        @Test
        @DisplayName("re-throws BadRequestException from AI layer without wrapping")
        void aiLayerThrowsBadRequest_rethrownDirectly() {
            when(advisorsSpec.call()).thenThrow(new BadRequestException("bad input"));

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);

            assertThatThrownBy(() -> chatService.chat(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("bad input");
        }

        @Test
        @DisplayName("re-throws RateLimitException from AI layer without wrapping")
        void aiLayerThrowsRateLimit_rethrownDirectly() {
            when(advisorsSpec.call()).thenThrow(new RateLimitException());

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);

            assertThatThrownBy(() -> chatService.chat(request))
                    .isInstanceOf(RateLimitException.class);
        }

        @Test
        @DisplayName("re-throws AiApiException from AI layer without wrapping")
        void aiLayerThrowsAiApiException_rethrownDirectly() {
            when(advisorsSpec.call()).thenThrow(
                    new AiApiException("upstream error", new RuntimeException()));

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);

            assertThatThrownBy(() -> chatService.chat(request))
                    .isInstanceOf(AiApiException.class);
        }

        @Test
        @DisplayName("re-throws AiTimeoutException from AI layer without wrapping")
        void aiLayerThrowsAiTimeout_rethrownDirectly() {
            when(advisorsSpec.call()).thenThrow(new AiTimeoutException());

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);

            assertThatThrownBy(() -> chatService.chat(request))
                    .isInstanceOf(AiTimeoutException.class);
        }

        @Test
        @DisplayName("re-throws ServiceUnavailableException from AI layer without wrapping")
        void aiLayerThrowsServiceUnavailable_rethrownDirectly() {
            when(advisorsSpec.call()).thenThrow(
                    new ServiceUnavailableException("down"));

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);

            assertThatThrownBy(() -> chatService.chat(request))
                    .isInstanceOf(ServiceUnavailableException.class);
        }

        // ── handleAiException() branches ─────────────────────────────────

        @Test
        @DisplayName("maps TimeoutException instance to AiTimeoutException (504)")
        void handleAiException_timeoutInstance_throwsAiTimeout() {
            // Use thenAnswer to sneakily throw a checked exception without Mockito complaining
            when(advisorsSpec.call()).thenAnswer(invocation -> {
                throw new TimeoutException("timed out");
            });

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);

            assertThatThrownBy(() -> chatService.chat(request))
                    .isInstanceOf(AiTimeoutException.class);
        }

        @Test
        @DisplayName("maps message containing 'timeout' keyword to AiTimeoutException (504)")
        void handleAiException_timeoutKeyword_throwsAiTimeout() {
            when(advisorsSpec.call()).thenThrow(
                    new RuntimeException("request timeout after 30s"));

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);

            assertThatThrownBy(() -> chatService.chat(request))
                    .isInstanceOf(AiTimeoutException.class);
        }

        @Test
        @DisplayName("maps message containing 'timed out' to AiTimeoutException (504)")
        void handleAiException_timedOutKeyword_throwsAiTimeout() {
            when(advisorsSpec.call()).thenThrow(
                    new RuntimeException("connection timed out"));

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);

            assertThatThrownBy(() -> chatService.chat(request))
                    .isInstanceOf(AiTimeoutException.class);
        }

        @Test
        @DisplayName("maps message containing '429' to RateLimitException (429)")
        void handleAiException_429Keyword_throwsRateLimit() {
            when(advisorsSpec.call()).thenThrow(
                    new RuntimeException("HTTP 429 from upstream"));

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);

            assertThatThrownBy(() -> chatService.chat(request))
                    .isInstanceOf(RateLimitException.class);
        }

        @Test
        @DisplayName("maps message containing '529' to RateLimitException (429)")
        void handleAiException_529Keyword_throwsRateLimit() {
            when(advisorsSpec.call()).thenThrow(
                    new RuntimeException("HTTP 529 overloaded"));

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);

            assertThatThrownBy(() -> chatService.chat(request))
                    .isInstanceOf(RateLimitException.class);
        }

        @Test
        @DisplayName("maps message containing 'rate limit' to RateLimitException (429)")
        void handleAiException_rateLimitKeyword_throwsRateLimit() {
            when(advisorsSpec.call()).thenThrow(
                    new RuntimeException("rate limit exceeded"));

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);

            assertThatThrownBy(() -> chatService.chat(request))
                    .isInstanceOf(RateLimitException.class);
        }

        @Test
        @DisplayName("maps message containing 'too many requests' to RateLimitException")
        void handleAiException_tooManyRequestsKeyword_throwsRateLimit() {
            when(advisorsSpec.call()).thenThrow(
                    new RuntimeException("too many requests"));

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);

            assertThatThrownBy(() -> chatService.chat(request))
                    .isInstanceOf(RateLimitException.class);
        }

        @Test
        @DisplayName("maps message containing 'overloaded' to RateLimitException")
        void handleAiException_overloadedKeyword_throwsRateLimit() {
            when(advisorsSpec.call()).thenThrow(
                    new RuntimeException("server overloaded"));

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);

            assertThatThrownBy(() -> chatService.chat(request))
                    .isInstanceOf(RateLimitException.class);
        }

        @Test
        @DisplayName("maps message containing 'connection refused' to ServiceUnavailableException (503)")
        void handleAiException_connectionRefused_throwsServiceUnavailable() {
            when(advisorsSpec.call()).thenThrow(
                    new RuntimeException("connection refused"));

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);

            assertThatThrownBy(() -> chatService.chat(request))
                    .isInstanceOf(ServiceUnavailableException.class);
        }

        @Test
        @DisplayName("maps message containing 'service unavailable' to ServiceUnavailableException (503)")
        void handleAiException_serviceUnavailableKeyword_throwsServiceUnavailable() {
            when(advisorsSpec.call()).thenThrow(
                    new RuntimeException("service unavailable"));

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);

            assertThatThrownBy(() -> chatService.chat(request))
                    .isInstanceOf(ServiceUnavailableException.class);
        }

        @Test
        @DisplayName("maps message containing '503' to ServiceUnavailableException")
        void handleAiException_503Keyword_throwsServiceUnavailable() {
            when(advisorsSpec.call()).thenThrow(
                    new RuntimeException("HTTP 503 from server"));

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);

            assertThatThrownBy(() -> chatService.chat(request))
                    .isInstanceOf(ServiceUnavailableException.class);
        }

        @Test
        @DisplayName("maps message containing 'unreachable' to ServiceUnavailableException")
        void handleAiException_unreachableKeyword_throwsServiceUnavailable() {
            when(advisorsSpec.call()).thenThrow(
                    new RuntimeException("host unreachable"));

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);

            assertThatThrownBy(() -> chatService.chat(request))
                    .isInstanceOf(ServiceUnavailableException.class);
        }

        @Test
        @DisplayName("maps message containing 'anthropic' to AiApiException (502)")
        void handleAiException_anthropicKeyword_throwsAiApi() {
            when(advisorsSpec.call()).thenThrow(
                    new RuntimeException("anthropic returned an error"));

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);

            assertThatThrownBy(() -> chatService.chat(request))
                    .isInstanceOf(AiApiException.class);
        }

        @Test
        @DisplayName("maps message containing 'upstream' to AiApiException (502)")
        void handleAiException_upstreamKeyword_throwsAiApi() {
            when(advisorsSpec.call()).thenThrow(
                    new RuntimeException("upstream failure"));

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);

            assertThatThrownBy(() -> chatService.chat(request))
                    .isInstanceOf(AiApiException.class);
        }

        @Test
        @DisplayName("maps message containing 'http' to AiApiException (502)")
        void handleAiException_httpKeyword_throwsAiApi() {
            when(advisorsSpec.call()).thenThrow(
                    new RuntimeException("http call failed"));

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);

            assertThatThrownBy(() -> chatService.chat(request))
                    .isInstanceOf(AiApiException.class);
        }

        @Test
        @DisplayName("maps message containing 'api' to AiApiException (502)")
        void handleAiException_apiKeyword_throwsAiApi() {
            when(advisorsSpec.call()).thenThrow(
                    new RuntimeException("api call rejected"));

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);

            assertThatThrownBy(() -> chatService.chat(request))
                    .isInstanceOf(AiApiException.class);
        }

        @Test
        @DisplayName("maps message containing '4xx' to AiApiException (502)")
        void handleAiException_4xxKeyword_throwsAiApi() {
            when(advisorsSpec.call()).thenThrow(
                    new RuntimeException("received 4xx from server"));

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);

            assertThatThrownBy(() -> chatService.chat(request))
                    .isInstanceOf(AiApiException.class);
        }

        @Test
        @DisplayName("maps message containing '5xx' to AiApiException (502)")
        void handleAiException_5xxKeyword_throwsAiApi() {
            when(advisorsSpec.call()).thenThrow(
                    new RuntimeException("received 5xx from server"));

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);

            assertThatThrownBy(() -> chatService.chat(request))
                    .isInstanceOf(AiApiException.class);
        }

        @Test
        @DisplayName("maps null exception message to ChatBaseException (500) catch-all")
        void handleAiException_nullMessage_throwsChatBase() {
            RuntimeException exWithNullMsg = mock(RuntimeException.class);
            when(exWithNullMsg.getMessage()).thenReturn(null);
            when(advisorsSpec.call()).thenThrow(exWithNullMsg);

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);

            assertThatThrownBy(() -> chatService.chat(request))
                    .isInstanceOf(ChatBaseException.class);
        }

        @Test
        @DisplayName("maps unknown exception message to ChatBaseException (500) catch-all")
        void handleAiException_unknownMessage_throwsChatBase() {
            when(advisorsSpec.call()).thenThrow(
                    new RuntimeException("some completely unknown failure xyz"));

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);

            assertThatThrownBy(() -> chatService.chat(request))
                    .isInstanceOf(ChatBaseException.class);
        }

        // ── extractText() null-guard branches ────────────────────────────

        @Test
        @DisplayName("returns empty reply text when ChatResponse is null")
        void extractText_nullChatResponse_returnsEmpty() {
            when(advisorsSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.chatResponse()).thenReturn(null);

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);
            ChatReply reply = chatService.chat(request);

            assertThat(reply.reply()).isEmpty();
        }

        @Test
        @DisplayName("returns empty reply text when Generation result is null")
        void extractText_nullResult_returnsEmpty() {
            ChatResponse aiResponse = mock(ChatResponse.class);
            when(aiResponse.getResult()).thenReturn(null);
            when(aiResponse.getResults()).thenReturn(List.of());
            when(aiResponse.getMetadata()).thenReturn(null);

            when(advisorsSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.chatResponse()).thenReturn(aiResponse);

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);
            ChatReply reply = chatService.chat(request);

            assertThat(reply.reply()).isEmpty();
        }

        @Test
        @DisplayName("returns empty reply text when AssistantMessage output is null")
        void extractText_nullOutput_returnsEmpty() {
            Generation generation = mock(Generation.class);
            when(generation.getOutput()).thenReturn(null);

            ChatResponse aiResponse = mock(ChatResponse.class);
            when(aiResponse.getResult()).thenReturn(generation);
            when(aiResponse.getResults()).thenReturn(List.of());
            when(aiResponse.getMetadata()).thenReturn(null);

            when(advisorsSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.chatResponse()).thenReturn(aiResponse);

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);
            ChatReply reply = chatService.chat(request);

            assertThat(reply.reply()).isEmpty();
        }

        @Test
        @DisplayName("returns empty reply text when getText() returns null")
        void extractText_nullText_returnsEmpty() {
            AssistantMessage assistantMsg = mock(AssistantMessage.class);
            when(assistantMsg.getText()).thenReturn(null);
            when(assistantMsg.getToolCalls()).thenReturn(null);

            Generation generation = mock(Generation.class);
            when(generation.getOutput()).thenReturn(assistantMsg);

            ChatResponse aiResponse = mock(ChatResponse.class);
            when(aiResponse.getResult()).thenReturn(generation);
            when(aiResponse.getResults()).thenReturn(List.of(generation));
            when(aiResponse.getMetadata()).thenReturn(null);

            when(advisorsSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.chatResponse()).thenReturn(aiResponse);

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);
            ChatReply reply = chatService.chat(request);

            assertThat(reply.reply()).isEmpty();
        }

        // ── extractToolsUsed() null-guard branches ────────────────────────

        @Test
        @DisplayName("returns empty toolsUsed list when getToolCalls() returns null")
        void extractToolsUsed_nullToolCalls_returnsEmptyList() {
            AssistantMessage assistantMsg = mock(AssistantMessage.class);
            when(assistantMsg.getText()).thenReturn("Hello!");
            when(assistantMsg.getToolCalls()).thenReturn(null);

            Generation generation = mock(Generation.class);
            when(generation.getOutput()).thenReturn(assistantMsg);

            ChatResponse aiResponse = mock(ChatResponse.class);
            when(aiResponse.getResult()).thenReturn(generation);
            when(aiResponse.getResults()).thenReturn(List.of(generation));
            when(aiResponse.getMetadata()).thenReturn(null);

            when(advisorsSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.chatResponse()).thenReturn(aiResponse);

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);
            ChatReply reply = chatService.chat(request);

            assertThat(reply.toolsUsed()).isEmpty();
        }

        @Test
        @DisplayName("returns empty toolsUsed when result output is null inside getResults()")
        void extractToolsUsed_nullOutputInResults_returnsEmptyList() {
            Generation generation = mock(Generation.class);
            when(generation.getOutput()).thenReturn(null);

            ChatResponse aiResponse = mock(ChatResponse.class);
            when(aiResponse.getResult()).thenReturn(null);
            when(aiResponse.getResults()).thenReturn(List.of(generation));
            when(aiResponse.getMetadata()).thenReturn(null);

            when(advisorsSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.chatResponse()).thenReturn(aiResponse);

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);
            ChatReply reply = chatService.chat(request);

            assertThat(reply.toolsUsed()).isEmpty();
        }

        @Test
        @DisplayName("collects multiple tool names from toolCalls")
        void extractToolsUsed_multipleToolCalls_returnsAllNames() {
            AssistantMessage assistantMsg = mock(AssistantMessage.class);
            when(assistantMsg.getText()).thenReturn("Done.");
            when(assistantMsg.getToolCalls()).thenReturn(List.of(
                    mockToolCall("get_weather"),
                    mockToolCall("track_order")
            ));

            Generation generation = mock(Generation.class);
            when(generation.getOutput()).thenReturn(assistantMsg);

            ChatResponse aiResponse = mock(ChatResponse.class);
            when(aiResponse.getResult()).thenReturn(generation);
            when(aiResponse.getResults()).thenReturn(List.of(generation));
            when(aiResponse.getMetadata()).thenReturn(mock(ChatResponseMetadata.class));

            when(advisorsSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.chatResponse()).thenReturn(aiResponse);

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);
            ChatReply reply = chatService.chat(request);

            assertThat(reply.toolsUsed()).containsExactlyInAnyOrder("get_weather", "track_order");
        }

        // ── extractModel() null-guard branches ────────────────────────────

        @Test
        @DisplayName("returns default model name when ChatResponse metadata is null")
        void extractModel_nullMetadata_returnsDefault() {
            ChatResponse aiResponse = buildMinimalChatResponse("Hi!");
            when(aiResponse.getMetadata()).thenReturn(null);

            when(advisorsSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.chatResponse()).thenReturn(aiResponse);

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);
            ChatReply reply = chatService.chat(request);

            assertThat(reply.model()).isEqualTo("claude-sonnet-4-20250514");
        }

        @Test
        @DisplayName("returns default model name when metadata.getModel() is null")
        void extractModel_nullModelInMetadata_returnsDefault() {
            ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
            when(metadata.getModel()).thenReturn(null);

            ChatResponse aiResponse = buildMinimalChatResponse("Hi!");
            when(aiResponse.getMetadata()).thenReturn(metadata);

            when(advisorsSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.chatResponse()).thenReturn(aiResponse);

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, false);
            ChatReply reply = chatService.chat(request);

            assertThat(reply.model()).isEqualTo("claude-sonnet-4-20250514");
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // stream()
    // ═════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("stream()")
    class StreamMethod {

        @Test
        @DisplayName("returns a Flux of tokens from the ChatClient stream")
        void happyPath_returnsTokenFlux() {
            when(advisorsSpec.stream()).thenReturn(streamResponseSpec);
            when(streamResponseSpec.content()).thenReturn(Flux.just("Hello", " world"));

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, true);

            Flux<String> result = chatService.stream(request);

            StepVerifier.create(result)
                    .expectNext("Hello")
                    .expectNext(" world")
                    .verifyComplete();
        }

        @Test
        @DisplayName("returns Flux.error(BadRequestException) when message is whitespace-only")
        void blankMessage_returnsFluxError() {
            ChatRequest request = new ChatRequest(SESSION_ID, "   ", true);

            Flux<String> result = chatService.stream(request);

            StepVerifier.create(result)
                    .expectErrorSatisfies(err -> {
                        assertThat(err).isInstanceOf(BadRequestException.class);
                        assertThat(err.getMessage()).contains("empty after trimming");
                    })
                    .verify();
        }

        @Test
        @DisplayName("generates UUID sessionId when stream request sessionId is null")
        void nullSessionId_generatesUuid() {
            when(advisorsSpec.stream()).thenReturn(streamResponseSpec);
            when(streamResponseSpec.content()).thenReturn(Flux.just("token"));

            ChatRequest request = new ChatRequest(null, MESSAGE, true);

            StepVerifier.create(chatService.stream(request))
                    .expectNext("token")
                    .verifyComplete();
        }

        @Test
        @DisplayName("propagates downstream error via doOnError logging")
        void streamError_propagatesFluxError() {
            RuntimeException cause = new RuntimeException("stream failed");
            when(advisorsSpec.stream()).thenReturn(streamResponseSpec);
            when(streamResponseSpec.content()).thenReturn(Flux.error(cause));

            ChatRequest request = new ChatRequest(SESSION_ID, MESSAGE, true);

            StepVerifier.create(chatService.stream(request))
                    .expectErrorMatches(err -> err.getMessage().equals("stream failed"))
                    .verify();
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // clearSession()
    // ═════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("clearSession()")
    class ClearSessionMethod {

        @Test
        @DisplayName("returns SessionClearResponse with the given sessionId")
        void happyPath_returnsSessionClearResponse() {
            SessionClearResponse response = chatService.clearSession(SESSION_ID);

            assertThat(response).isNotNull();
            assertThat(response.sessionId()).isEqualTo(SESSION_ID);
        }

        @Test
        @DisplayName("throws BadRequestException when sessionId is null")
        void nullSessionId_throwsBadRequest() {
            assertThatThrownBy(() -> chatService.clearSession(null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("sessionId must not be blank");
        }

        @Test
        @DisplayName("throws BadRequestException when sessionId is blank")
        void blankSessionId_throwsBadRequest() {
            assertThatThrownBy(() -> chatService.clearSession("   "))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("sessionId must not be blank");
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // Helpers
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Builds a minimal valid ChatResponse stub with the given reply text.
     * Returns a lenient mock so tests can override metadata independently.
     */
    private ChatResponse buildMinimalChatResponse(String text) {
        AssistantMessage assistantMsg = mock(AssistantMessage.class);
        lenient().when(assistantMsg.getText()).thenReturn(text);
        lenient().when(assistantMsg.getToolCalls()).thenReturn(List.of());

        Generation generation = mock(Generation.class);
        lenient().when(generation.getOutput()).thenReturn(assistantMsg);

        ChatResponse aiResponse = mock(ChatResponse.class);
        lenient().when(aiResponse.getResult()).thenReturn(generation);
        lenient().when(aiResponse.getResults()).thenReturn(List.of(generation));
        lenient().when(aiResponse.getMetadata()).thenReturn(null);
        return aiResponse;
    }

    /**
     * Creates a minimal {@link AssistantMessage.ToolCall} stub with the given name.
     */
    private AssistantMessage.ToolCall mockToolCall(String name) {
        // Instantiates the record directly without using Mockito
        return new AssistantMessage.ToolCall("call_123", "function", name, "{}");
    }
}