package com.agenticai.chatbot.model;

import org.junit.jupiter.api.Test;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {

    @Test
    void testRecordAccessorsAndConstructor() {
        Instant now = Instant.now();
        ApiResponse.ApiError error = new ApiResponse.ApiError("CUSTOM_ERR", "Details here");
        ApiResponse<String> response = new ApiResponse<>(true, 200, "OK", "Payload", error, "/api/test", now);

        // Verify record property accessors
        assertTrue(response.success());
        assertEquals(200, response.status());
        assertEquals("OK", response.message());
        assertEquals("Payload", response.data());
        assertNotNull(response.error());
        assertEquals("CUSTOM_ERR", response.error().code());
        assertEquals("Details here", response.error().detail());
        assertEquals("/api/test", response.path());
        assertEquals(now, response.timestamp());
    }

    @Test
    void testInternalErrorFactory() {
        ApiResponse<Object> response = ApiResponse.internalError("Database crash", "/api/chat");

        assertFalse(response.success());
        assertEquals(500, response.status());
        assertEquals("Internal Server Error", response.message());
        assertNull(response.data());
        assertNotNull(response.error());
        assertEquals("INTERNAL_ERROR", response.error().code());
        assertEquals("Database crash", response.error().detail());
        assertEquals("/api/chat", response.path());
        assertNotNull(response.timestamp());
    }

    @Test
    void testBadGatewayFactory() {
        ApiResponse<Object> response = ApiResponse.badGateway("Anthropic API failed", "/api/chat/stream");

        assertFalse(response.success());
        assertEquals(502, response.status());
        assertEquals("Bad Gateway", response.message());
        assertNull(response.data());
        assertNotNull(response.error());
        assertEquals("AI_API_ERROR", response.error().code());
        assertEquals("Anthropic API failed", response.error().detail());
        assertEquals("/api/chat/stream", response.path());
        assertNotNull(response.timestamp());
    }

    @Test
    void testServiceUnavailableFactory() {
        ApiResponse<Object> response = ApiResponse.serviceUnavailable("Model is overloading", "/api/chat");

        assertFalse(response.success());
        assertEquals(503, response.status());
        assertEquals("Service Unavailable", response.message());
        assertNull(response.data());
        assertNotNull(response.error());
        assertEquals("SERVICE_UNAVAILABLE", response.error().code());
        assertEquals("Model is overloading", response.error().detail());
        assertEquals("/api/chat", response.path());
        assertNotNull(response.timestamp());
    }

    @Test
    void testGatewayTimeoutFactory() {
        ApiResponse<Object> response = ApiResponse.gatewayTimeout("Read timed out", "/api/chat");

        assertFalse(response.success());
        assertEquals(504, response.status());
        assertEquals("Gateway Timeout", response.message());
        assertNull(response.data());
        assertNotNull(response.error());
        assertEquals("GATEWAY_TIMEOUT", response.error().code());
        assertEquals("Read timed out", response.error().detail());
        assertEquals("/api/chat", response.path());
        assertNotNull(response.timestamp());
    }

    @Test
    void testNoContentFactory() {
        ApiResponse<Object> response = ApiResponse.noContent("/api/chat/clear");

        assertTrue(response.success());
        assertEquals(204, response.status());
        assertEquals("No Content", response.message());
        assertNull(response.data());
        assertNull(response.error());
        assertEquals("/api/chat/clear", response.path());
        assertNotNull(response.timestamp());
    }

    @Test
    void testNotFoundFactory() {
        ApiResponse<Object> response = ApiResponse.notFound("Session not found", "/api/chat/status");

        assertFalse(response.success());
        assertEquals(404, response.status());
        assertEquals("Not Found", response.message());
        assertNull(response.data());
        assertNotNull(response.error());
        assertEquals("NOT_FOUND", response.error().code());
        assertEquals("Session not found", response.error().detail());
        assertEquals("/api/chat/status", response.path());
        assertNotNull(response.timestamp());
    }

    @Test
    void testMethodNotAllowedFactory() {
        ApiResponse<Object> response = ApiResponse.methodNotAllowed("POST not supported", "/api/info");

        assertFalse(response.success());
        assertEquals(405, response.status());
        assertEquals("Method Not Allowed", response.message());
        assertNull(response.data());
        assertNotNull(response.error());
        assertEquals("METHOD_NOT_ALLOWED", response.error().code());
        assertEquals("POST not supported", response.error().detail());
        assertEquals("/api/info", response.path());
        assertNotNull(response.timestamp());
    }

    @Test
    void testUnprocessableFactory() {
        ApiResponse<Object> response = ApiResponse.unprocessable("Business validation failed", "/api/chat");

        assertFalse(response.success());
        assertEquals(422, response.status());
        assertEquals("Unprocessable Entity", response.message());
        assertNull(response.data());
        assertNotNull(response.error());
        assertEquals("UNPROCESSABLE_ENTITY", response.error().code());
        assertEquals("Business validation failed", response.error().detail());
        assertEquals("/api/chat", response.path());
        assertNotNull(response.timestamp());
    }
}