package com.agenticai.chatbot.advisor;

import com.agenticai.chatbot.model.ApiResponse;
import com.agenticai.chatbot.model.ChatException.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 *
 * <p>All ApiResponse accessor calls use Java record syntax:
 * success(), status(), message(), data(), error(), path(), timestamp().
 * ApiError accessor calls: code(), detail().
 *
 * <p>Coverage targets — every handler method and every branch:
 * <pre>
 * handleValidation()           → FieldError with message, null message fallback,
 *                                non-FieldError filtered, multiple errors, path check
 * handleMalformedJson()        → 400 with fixed message, null ex message
 * handleBadRequest()           → 400 with exception message, message content check
 * handleNotFound()             → NoHandlerFoundException 404, different URI
 * handleResourceNotFound()     → ResourceNotFoundException 404
 * handleMethodNotAllowed()     → 405 with supported methods, no supported methods
 * handleUnsupportedMediaType() → 415 non-null content type, null content type, timestamp
 * handleUnprocessable()        → 422
 * handleRateLimit()            → 429 Retry-After:60, custom message
 * handleChatBase()             → 500, safe message check
 * handleAiApi()                → 502, body reflects message
 * handleServiceUnavailable()   → 503 Retry-After:30, exact value check
 * handleAiTimeout()            → 504 no-arg, custom message
 * handleGeneric()              → 500 RuntimeException, safe message, NPE, checked Exception
 * cross-cutting                → success=false for all, path population, timestamp
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler handler;

    private MockHttpServletRequest request;

    private static final String REQUEST_URI = "/api/chat";

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setRequestURI(REQUEST_URI);
    }

    // =========================================================================
    // 400 — handleValidation()
    // =========================================================================

    @Nested
    @DisplayName("handleValidation() → 400")
    class HandleValidation {

        @Test
        @DisplayName("returns 400 with field-error map when FieldError has a non-null message")
        void fieldError_withMessage_returns400WithFieldMap() {
            FieldError fieldError = new FieldError("chatRequest", "message",
                    "must not be blank");

            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));

            MethodArgumentNotValidException ex =
                    mock(MethodArgumentNotValidException.class);
            when(ex.getBindingResult()).thenReturn(bindingResult);

            ResponseEntity<ApiResponse<Map<String, String>>> response =
                    handler.handleValidation(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            ApiResponse<Map<String, String>> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.success()).isFalse();
            assertThat(body.status()).isEqualTo(400);
            assertThat(body.message()).isEqualTo("Validation Failed");
            assertThat(body.path()).isEqualTo(REQUEST_URI);

            Map<String, String> fieldErrors = body.data();
            assertThat(fieldErrors).containsEntry("message", "must not be blank");

            ApiResponse.ApiError error = body.error();
            assertThat(error.code()).isEqualTo("VALIDATION_ERROR");
            assertThat(error.detail())
                    .isEqualTo("One or more request fields failed validation.");
        }

        @Test
        @DisplayName("uses 'invalid value' fallback when FieldError default message is null")
        void fieldError_nullMessage_usesFallback() {
            FieldError fieldError = new FieldError(
                    "chatRequest", "sessionId",
                    null, false, null, null, null);

            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));

            MethodArgumentNotValidException ex =
                    mock(MethodArgumentNotValidException.class);
            when(ex.getBindingResult()).thenReturn(bindingResult);

            ResponseEntity<ApiResponse<Map<String, String>>> response =
                    handler.handleValidation(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data())
                    .containsEntry("sessionId", "invalid value");
        }

        @Test
        @DisplayName("filters out non-FieldError ObjectErrors — result data map is empty")
        void nonFieldErrors_areFiltered_resultDataMapEmpty() {
            org.springframework.validation.ObjectError objectError =
                    new org.springframework.validation.ObjectError(
                            "chatRequest", "Object-level error");

            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getAllErrors()).thenReturn(List.of(objectError));

            MethodArgumentNotValidException ex =
                    mock(MethodArgumentNotValidException.class);
            when(ex.getBindingResult()).thenReturn(bindingResult);

            ResponseEntity<ApiResponse<Map<String, String>>> response =
                    handler.handleValidation(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data()).isEmpty();
        }

        @Test
        @DisplayName("handles multiple FieldErrors and maps all fields correctly")
        void multipleFieldErrors_allMappedCorrectly() {
            FieldError error1 = new FieldError("req", "message", "must not be blank");
            FieldError error2 = new FieldError("req", "sessionId", "size exceeded");

            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getAllErrors()).thenReturn(List.of(error1, error2));

            MethodArgumentNotValidException ex =
                    mock(MethodArgumentNotValidException.class);
            when(ex.getBindingResult()).thenReturn(bindingResult);

            ResponseEntity<ApiResponse<Map<String, String>>> response =
                    handler.handleValidation(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            Map<String, String> data = response.getBody().data();
            assertThat(data)
                    .containsEntry("message", "must not be blank")
                    .containsEntry("sessionId", "size exceeded");
        }

        @Test
        @DisplayName("path in body matches the request URI")
        void responsePath_matchesRequestUri() {
            FieldError fieldError = new FieldError("req", "message", "required");
            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));

            MethodArgumentNotValidException ex =
                    mock(MethodArgumentNotValidException.class);
            when(ex.getBindingResult()).thenReturn(bindingResult);

            ResponseEntity<ApiResponse<Map<String, String>>> response =
                    handler.handleValidation(ex, request);

            assertThat(response.getBody().path()).isEqualTo(REQUEST_URI);
        }

        @Test
        @DisplayName("timestamp field is populated (not null)")
        void timestamp_isPopulated() {
            FieldError fieldError = new FieldError("req", "message", "required");
            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));

            MethodArgumentNotValidException ex =
                    mock(MethodArgumentNotValidException.class);
            when(ex.getBindingResult()).thenReturn(bindingResult);

            ResponseEntity<ApiResponse<Map<String, String>>> response =
                    handler.handleValidation(ex, request);

            assertThat(response.getBody().timestamp()).isNotNull();
        }
    }

    // =========================================================================
    // 400 — handleMalformedJson()
    // =========================================================================

    @Nested
    @DisplayName("handleMalformedJson() → 400")
    class HandleMalformedJson {

        @Test
        @DisplayName("returns 400 with malformed-JSON message and correct path")
        void malformedJson_returns400WithDetail() {
            HttpMessageNotReadableException ex =
                    mock(HttpMessageNotReadableException.class);
            when(ex.getMessage()).thenReturn("JSON parse error: Unexpected character");

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleMalformedJson(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            ApiResponse<Void> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.success()).isFalse();
            assertThat(body.error().detail()).contains("malformed or missing");
            assertThat(body.path()).isEqualTo(REQUEST_URI);
        }

        @Test
        @DisplayName("handles null exception message without throwing NullPointerException")
        void nullExceptionMessage_doesNotThrow() {
            HttpMessageNotReadableException ex =
                    mock(HttpMessageNotReadableException.class);
            when(ex.getMessage()).thenReturn(null);

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleMalformedJson(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
        }
    }

    // =========================================================================
    // 400 — handleBadRequest()
    // =========================================================================

    @Nested
    @DisplayName("handleBadRequest() → 400")
    class HandleBadRequest {

        @Test
        @DisplayName("returns 400 with correct status, success=false and path")
        void badRequest_returns400WithCorrectBody() {
            BadRequestException ex = new BadRequestException("Message is empty.");

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleBadRequest(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            ApiResponse<Void> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.success()).isFalse();
            assertThat(body.path()).isEqualTo(REQUEST_URI);
        }

        @Test
        @DisplayName("response message field is not blank")
        void badRequest_messageFieldNotBlank() {
            BadRequestException ex = new BadRequestException("Custom bad request reason");

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleBadRequest(ex, request);

            assertThat(response.getBody().message()).isNotBlank();
        }
    }

    // =========================================================================
    // 404 — handleNotFound()
    // =========================================================================

    @Nested
    @DisplayName("handleNotFound() → 404")
    class HandleNotFound {

        @Test
        @DisplayName("returns 404 with status=404, success=false and path")
        void noHandler_returns404WithBody() {
            NoHandlerFoundException ex = new NoHandlerFoundException(
                    "GET", REQUEST_URI,
                    new org.springframework.http.HttpHeaders());

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleNotFound(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

            ApiResponse<Void> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.success()).isFalse();
            assertThat(body.status()).isEqualTo(404);
            assertThat(body.path()).isEqualTo(REQUEST_URI);
        }

        @Test
        @DisplayName("returns 404 for a different URI and method")
        void noHandler_differentUriAndMethod_returns404() {
            request.setRequestURI("/api/unknown");
            NoHandlerFoundException ex = new NoHandlerFoundException(
                    "DELETE", "/api/unknown",
                    new org.springframework.http.HttpHeaders());

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleNotFound(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // =========================================================================
    // 404 — handleResourceNotFound()
    // =========================================================================

    @Nested
    @DisplayName("handleResourceNotFound() → 404")
    class HandleResourceNotFound {

        @Test
        @DisplayName("returns 404 with status=404, success=false and path")
        void resourceNotFound_returns404() {
            ResourceNotFoundException ex =
                    new ResourceNotFoundException("Order ORD-9999 not found.", "ORD-9999");

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleResourceNotFound(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

            ApiResponse<Void> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.success()).isFalse();
            assertThat(body.status()).isEqualTo(404);
            assertThat(body.path()).isEqualTo(REQUEST_URI);
        }
    }

    // =========================================================================
    // 405 — handleMethodNotAllowed()
    // =========================================================================

    @Nested
    @DisplayName("handleMethodNotAllowed() → 405")
    class HandleMethodNotAllowed {

        @Test
        @DisplayName("returns 405 with status=405, success=false and path")
        void methodNotAllowed_returns405WithSupportedMethods() {
            HttpRequestMethodNotSupportedException ex =
                    new HttpRequestMethodNotSupportedException(
                            "DELETE", List.of("GET", "POST"));

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleMethodNotAllowed(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);

            ApiResponse<Void> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.success()).isFalse();
            assertThat(body.status()).isEqualTo(405);
            assertThat(body.path()).isEqualTo(REQUEST_URI);
        }

        @Test
        @DisplayName("returns 405 when no supported methods are specified")
        void methodNotAllowed_noSupportedMethods_returns405() {
            HttpRequestMethodNotSupportedException ex =
                    new HttpRequestMethodNotSupportedException("PATCH");

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleMethodNotAllowed(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        }
    }

    // =========================================================================
    // 415 — handleUnsupportedMediaType()
    // =========================================================================

    @Nested
    @DisplayName("handleUnsupportedMediaType() → 415")
    class HandleUnsupportedMediaType {

        @Test
        @DisplayName("returns 415 with APPLICATION_JSON content-type header and ApiError details")
        void unsupportedMediaType_nonNullContentType_returns415() {
            HttpMediaTypeNotSupportedException ex =
                    new HttpMediaTypeNotSupportedException(
                            MediaType.TEXT_PLAIN,
                            List.of(MediaType.APPLICATION_JSON));

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleUnsupportedMediaType(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
            assertThat(response.getHeaders().getContentType())
                    .isEqualTo(MediaType.APPLICATION_JSON);

            ApiResponse<Void> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.success()).isFalse();
            assertThat(body.status()).isEqualTo(415);
            assertThat(body.message()).isEqualTo("Unsupported Media Type");
            assertThat(body.path()).isEqualTo(REQUEST_URI);

            ApiResponse.ApiError error = body.error();
            assertThat(error.code()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
            assertThat(error.detail()).contains("Use 'application/json'.");
        }

        @Test
        @DisplayName("returns 415 without NPE when getContentType() returns null")
        void unsupportedMediaType_nullContentType_returns415WithoutNpe() {
            HttpMediaTypeNotSupportedException ex =
                    mock(HttpMediaTypeNotSupportedException.class);
            when(ex.getContentType()).thenReturn(null);

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleUnsupportedMediaType(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);

            ApiResponse<Void> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.error().detail()).contains("is not supported");
        }

        @Test
        @DisplayName("timestamp field is populated (not null)")
        void unsupportedMediaType_timestampPopulated() {
            HttpMediaTypeNotSupportedException ex =
                    new HttpMediaTypeNotSupportedException(
                            MediaType.TEXT_XML,
                            List.of(MediaType.APPLICATION_JSON));

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleUnsupportedMediaType(ex, request);

            assertThat(response.getBody().timestamp()).isNotNull();
        }
    }

    // =========================================================================
    // 422 — handleUnprocessable()
    // =========================================================================

    @Nested
    @DisplayName("handleUnprocessable() → 422")
    class HandleUnprocessable {

        @Test
        @DisplayName("returns 422 with status=422, success=false and correct path")
        void unprocessable_returns422() {
            UnprocessableException ex =
                    new UnprocessableException("Input cannot be processed in current state.");

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleUnprocessable(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

            ApiResponse<Void> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.success()).isFalse();
            assertThat(body.status()).isEqualTo(422);
            assertThat(body.path()).isEqualTo(REQUEST_URI);
        }
    }

    // =========================================================================
    // 429 — handleRateLimit()
    // =========================================================================

    @Nested
    @DisplayName("handleRateLimit() → 429")
    class HandleRateLimit {

        @Test
        @DisplayName("returns 429 with Retry-After: 60 header, status=429 and success=false")
        void rateLimit_returns429WithRetryAfterHeader() {
            RateLimitException ex = new RateLimitException();

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleRateLimit(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("60");

            ApiResponse<Void> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.success()).isFalse();
            assertThat(body.status()).isEqualTo(429);
            assertThat(body.path()).isEqualTo(REQUEST_URI);
        }

        @Test
        @DisplayName("Retry-After header is exactly '60' when exception has a custom message")
        void rateLimit_customMessage_retryAfterIsStillSixty() {
            RateLimitException ex = new RateLimitException("Too many requests — slow down.");

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleRateLimit(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("60");
        }
    }

    // =========================================================================
    // 500 — handleChatBase()
    // =========================================================================

    @Nested
    @DisplayName("handleChatBase() → 500")
    class HandleChatBase {

        @Test
        @DisplayName("returns 500 with status=500, success=false and correct path")
        void chatBase_returns500() {
            ChatBaseException ex =
                    new ChatBaseException("Internal chat failure.", new RuntimeException("root"));

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleChatBase(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

            ApiResponse<Void> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.success()).isFalse();
            assertThat(body.status()).isEqualTo(500);
            assertThat(body.path()).isEqualTo(REQUEST_URI);
        }

        @Test
        @DisplayName("hides raw exception detail — client always sees the generic safe message in error detail")
        void chatBase_safeMessageSentToClient() {
            ChatBaseException ex =
                    new ChatBaseException("Sensitive internal info", new RuntimeException());

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleChatBase(ex, request);

            // message() holds the HTTP status phrase e.g. "Internal Server Error"
            // The safe human-readable text is in error().detail()
            assertThat(response.getBody().error().detail())
                    .isEqualTo("An internal error occurred. Please try again.");
        }
    }

    // =========================================================================
    // 502 — handleAiApi()
    // =========================================================================

    @Nested
    @DisplayName("handleAiApi() → 502")
    class HandleAiApi {

        @Test
        @DisplayName("returns 502 with status=502, success=false and correct path")
        void aiApi_returns502() {
            AiApiException ex = new AiApiException(
                    "Upstream Anthropic API returned 500.", new RuntimeException());

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleAiApi(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);

            ApiResponse<Void> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.success()).isFalse();
            assertThat(body.status()).isEqualTo(502);
            assertThat(body.path()).isEqualTo(REQUEST_URI);
        }

        @Test
        @DisplayName("returns 502 when AiApiException carries a root cause")
        void aiApi_withRootCause_returns502() {
            AiApiException ex = new AiApiException("Specific upstream error detail.",
                    new RuntimeException("root cause"));

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleAiApi(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(response.getBody()).isNotNull();
        }
    }

    // =========================================================================
    // 503 — handleServiceUnavailable()
    // =========================================================================

    @Nested
    @DisplayName("handleServiceUnavailable() → 503")
    class HandleServiceUnavailable {

        @Test
        @DisplayName("returns 503 with Retry-After: 30 header, status=503 and success=false")
        void serviceUnavailable_returns503WithRetryAfterHeader() {
            ServiceUnavailableException ex =
                    new ServiceUnavailableException("AI service is temporarily down.");

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleServiceUnavailable(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("30");

            ApiResponse<Void> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.success()).isFalse();
            assertThat(body.status()).isEqualTo(503);
            assertThat(body.path()).isEqualTo(REQUEST_URI);
        }

        @Test
        @DisplayName("Retry-After value is exactly '30' (not 60)")
        void serviceUnavailable_retryAfterIsThirty_notSixty() {
            ServiceUnavailableException ex =
                    new ServiceUnavailableException("down");

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleServiceUnavailable(ex, request);

            assertThat(response.getHeaders().getFirst("Retry-After"))
                    .isEqualTo("30")
                    .isNotEqualTo("60");
        }
    }

    // =========================================================================
    // 504 — handleAiTimeout()
    // =========================================================================

    @Nested
    @DisplayName("handleAiTimeout() → 504")
    class HandleAiTimeout {

        @Test
        @DisplayName("returns 504 with status=504, success=false and correct path")
        void aiTimeout_returns504() {
            AiTimeoutException ex = new AiTimeoutException();

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleAiTimeout(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);

            ApiResponse<Void> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.success()).isFalse();
            assertThat(body.status()).isEqualTo(504);
            assertThat(body.path()).isEqualTo(REQUEST_URI);
        }

        @Test
        @DisplayName("returns 504 when AiTimeoutException carries a custom message")
        void aiTimeout_withCustomMessage_returns504() {
            AiTimeoutException ex = new AiTimeoutException("Claude did not respond in time.");

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleAiTimeout(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
            assertThat(response.getBody()).isNotNull();
        }
    }

    // =========================================================================
    // 500 — handleGeneric() — catch-all
    // =========================================================================

    @Nested
    @DisplayName("handleGeneric() → 500 catch-all")
    class HandleGeneric {

        @Test
        @DisplayName("returns 500 with status=500, success=false and correct path")
        void genericException_returns500() {
            Exception ex = new RuntimeException("Completely unexpected failure");

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleGeneric(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

            ApiResponse<Void> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.success()).isFalse();
            assertThat(body.status()).isEqualTo(500);
            assertThat(body.path()).isEqualTo(REQUEST_URI);
        }

        @Test
        @DisplayName("hides raw exception detail — client always sees the generic safe message in error detail")
        void genericException_safeMessageSentToClient() {
            Exception ex = new RuntimeException("Sensitive stack trace detail");

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleGeneric(ex, request);

            assertThat(response.getBody().error().detail())
                    .isEqualTo("An unexpected error occurred. Please try again.");
        }

        @Test
        @DisplayName("handles NullPointerException (null message) without secondary failure")
        void nullPointerException_handledSafely() {
            Exception ex = new NullPointerException();

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleGeneric(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("handles checked Exception subclass (IOException) correctly")
        void checkedExceptionSubclass_returns500() {
            Exception ex = new java.io.IOException("disk full");

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleGeneric(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // =========================================================================
    // Cross-cutting: response envelope structure
    // =========================================================================

    @Nested
    @DisplayName("Response envelope — cross-cutting guarantees")
    class ResponseEnvelope {

        @Test
        @DisplayName("every handler returns success() == false")
        void allHandlers_returnSuccessFalse() {
            assertThat(handler.handleBadRequest(
                            new BadRequestException("x"), request)
                    .getBody().success()).isFalse();

            assertThat(handler.handleResourceNotFound(
                            new ResourceNotFoundException("x", "x"), request)
                    .getBody().success()).isFalse();

            assertThat(handler.handleRateLimit(
                            new RateLimitException(), request)
                    .getBody().success()).isFalse();

            assertThat(handler.handleChatBase(
                            new ChatBaseException("x", new RuntimeException()), request)
                    .getBody().success()).isFalse();

            assertThat(handler.handleAiApi(
                            new AiApiException("x", new RuntimeException()), request)
                    .getBody().success()).isFalse();

            assertThat(handler.handleServiceUnavailable(
                            new ServiceUnavailableException("x"), request)
                    .getBody().success()).isFalse();

            assertThat(handler.handleAiTimeout(
                            new AiTimeoutException(), request)
                    .getBody().success()).isFalse();

            assertThat(handler.handleGeneric(
                            new RuntimeException("x"), request)
                    .getBody().success()).isFalse();
        }

        @Test
        @DisplayName("every handler populates path() from the request URI")
        void allHandlers_populatePath() {
            request.setRequestURI("/api/test-path");

            assertThat(handler.handleBadRequest(
                            new BadRequestException("x"), request)
                    .getBody().path()).isEqualTo("/api/test-path");

            assertThat(handler.handleRateLimit(
                            new RateLimitException(), request)
                    .getBody().path()).isEqualTo("/api/test-path");

            assertThat(handler.handleGeneric(
                            new RuntimeException(), request)
                    .getBody().path()).isEqualTo("/api/test-path");
        }
    }
}