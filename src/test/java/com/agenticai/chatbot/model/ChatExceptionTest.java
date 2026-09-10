package com.agenticai.chatbot.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatExceptionTest {

    @Test
    void testChatBaseException() {
        ChatException.ChatBaseException ex = new ChatException.ChatBaseException("Base error message");
        assertEquals("Base error message", ex.getMessage());

        RuntimeException cause = new RuntimeException("Root cause");
        ChatException.ChatBaseException exWithCause = new ChatException.ChatBaseException("Base error with cause", cause);
        assertEquals("Base error with cause", exWithCause.getMessage());
        assertEquals(cause, exWithCause.getCause());
    }

    @Test
    void testBadRequestException() {
        ChatException.BadRequestException ex = new ChatException.BadRequestException("Malformed prompt input");
        assertEquals("Malformed prompt input", ex.getMessage());
    }

    @Test
    void testResourceNotFoundException() {
        ChatException.ResourceNotFoundException ex = new ChatException.ResourceNotFoundException("Session", "sess-999");
        assertEquals("Session not found: sess-999", ex.getMessage());
    }

    @Test
    void testUnprocessableException() {
        ChatException.UnprocessableException ex = new ChatException.UnprocessableException("Business validation error");
        assertEquals("Business validation error", ex.getMessage());
    }

    @Test
    void testRateLimitException() {
        // Test default message constructor
        ChatException.RateLimitException defaultEx = new ChatException.RateLimitException();
        assertEquals("Rate limit exceeded. Please wait before sending another message.", defaultEx.getMessage());

        // Test explicit message constructor
        ChatException.RateLimitException explicitEx = new ChatException.RateLimitException("Custom quota hit");
        assertEquals("Custom quota hit", explicitEx.getMessage());
    }

    @Test
    void testAiApiException() {
        ChatException.AiApiException ex = new ChatException.AiApiException("Upstream model error");
        assertEquals("Upstream model error", ex.getMessage());

        IllegalArgumentException cause = new IllegalArgumentException("Invalid API Key format");
        ChatException.AiApiException exWithCause = new ChatException.AiApiException("Upstream credential failure", cause);
        assertEquals("Upstream credential failure", exWithCause.getMessage());
        assertEquals(cause, exWithCause.getCause());
    }

    @Test
    void testServiceUnavailableException() {
        ChatException.ServiceUnavailableException ex = new ChatException.ServiceUnavailableException("Model down for maintenance");
        assertEquals("Model down for maintenance", ex.getMessage());
    }

    @Test
    void testAiTimeoutException() {
        // Test default message constructor
        ChatException.AiTimeoutException defaultEx = new ChatException.AiTimeoutException();
        assertEquals("AI model timed out. Please try again.", defaultEx.getMessage());

        // Test explicit message constructor
        ChatException.AiTimeoutException explicitEx = new ChatException.AiTimeoutException("Network socket timeout");
        assertEquals("Network socket timeout", explicitEx.getMessage());
    }
}