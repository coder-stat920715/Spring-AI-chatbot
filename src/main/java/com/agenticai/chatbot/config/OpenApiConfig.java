package com.agenticai.chatbot.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * SpringDoc / Swagger UI configuration for the Spring AI Chatbot API.
 * <h3>Features</h3>
 * <ul>
 *   <li>JWT Bearer authentication scheme — shows "Authorize 🔒" button in Swagger UI.</li>
 *   <li>API key (X-API-KEY) as a secondary auth option.</li>
 *   <li>Global security requirement — all endpoints show the lock icon.</li>
 *   <li>Rich API metadata (title, description, contact, license, version).</li>
 *   <li>Server definitions for local and production environments.</li>
 *   <li>Tag groupings matching the controller structure.</li>
 * </ul>
 * <h3>Access URLs</h3>
 * <ul>
 *   <li>Swagger UI : {@code http://localhost:8080/swagger-ui.html}</li>
 *   <li>OpenAPI JSON: {@code http://localhost:8080/v3/api-docs}</li>
 *   <li>OpenAPI YAML: {@code http://localhost:8080/v3/api-docs.yaml}</li>
 * </ul>
 * <h3>How to authenticate in Swagger UI</h3>
 * <ol>
 *   <li>Call {@code POST /api/auth/login} with demo credentials.</li>
 *   <li>Copy the {@code accessToken} from the response.</li>
 *   <li>Click the "Authorize 🔒" button at the top of the page.</li>
 *   <li>Paste the token in the {@code BearerAuth} field (without "Bearer " prefix).</li>
 *   <li>Click "Authorize" — all subsequent requests will include the header.</li>
 * </ol>
 */
@Configuration
// ── Class-level annotations register security schemes globally ────────────
@OpenAPIDefinition(
        info = @Info(
                title       = "🤖 Spring AI Agentic Chatbot API",
                version     = "1.0.0",
                description = """
                **Spring Boot 4 + Spring AI 2.0 Agentic Chatbot**
                
                A production-ready REST API exposing an AI-powered chatbot backed by
                **Anthropic Claude** via Spring AI. The agent autonomously selects tools
                (weather, order tracking, calculator, product info) based on the user's message.
                
                ---
                
                ### Authentication
                All `/api/chat/**` endpoints require a valid **JWT Bearer token**.
                
                1. Register a user: `POST /api/auth/register`
                2. Login to get a token: `POST /api/auth/login`
                3. Click **Authorize 🔒** above and paste the `accessToken`
                
                ### Demo Credentials
                | Username | Password    | Role        |
                |----------|-------------|-------------|
                | admin    | Admin@1234  | ADMIN+USER  |
                | user     | User@1234   | USER        |
                
                ### Agentic Tools Available
                | Tool             | Trigger Example                         |
                |------------------|-----------------------------------------|
                | `get_weather`    | "What is the weather in London?"        |
                | `track_order`    | "Track my order ORD-1002"               |
                | `calculate`      | "What is 15% tip on $128.50?"           |
                | `get_product_info` | "Tell me about PROD-001"              |
                """,
                contact = @Contact(
                        name  = "API Support",
                        email = "support@agenticai.com",
                        url   = "https://github.com/souptiktat/spring-ai-chatbot"
                ),
                license = @License(
                        name = "Apache 2.0",
                        url  = "https://www.apache.org/licenses/LICENSE-2.0"
                )
        ),
        servers = {
                @Server(url = "http://localhost:8080",       description = "Local Development"),
                @Server(url = "https://api.agenticai.com",   description = "Production")
        },
        // Global security requirement — all operations require BearerAuth UNLESS
        // overridden at the operation level with @SecurityRequirement(name = "")
        security = {
                @SecurityRequirement(name = "BearerAuth"),
                @SecurityRequirement(name = "ApiKeyAuth")
        }
)

// ── Security scheme definitions ───────────────────────────────────────────
@SecuritySchemes({

        @SecurityScheme(
                name        = "BearerAuth",
                type        = SecuritySchemeType.HTTP,
                scheme      = "bearer",
                bearerFormat = "JWT",
                description = """
                **JWT Bearer Token Authentication**
                
                Obtain a token from `POST /api/auth/login`, then paste it here
                (without the "Bearer " prefix). The UI will add the header automatically.
                
                Token format: `eyJhbGciOiJIUzI1NiJ9...`
                """
        ),

        @SecurityScheme(
                name        = "ApiKeyAuth",
                type        = SecuritySchemeType.APIKEY,
                in          = SecuritySchemeIn.HEADER,
                paramName   = "X-API-KEY",
                description = "Optional API key for machine-to-machine access (future use)."
        )
})
public class OpenApiConfig {

    /**
     * Programmatic OpenAPI bean — adds global response examples and tag descriptions.
     *
     * <p>This complements the annotation-based config above by adding:
     * <ul>
     *   <li>Detailed tag descriptions with emoji for better readability in Swagger UI</li>
     *   <li>Global 401/403/429/500 response examples reused across operations</li>
     * </ul>
     */
    @Bean
    public OpenAPI customOpenAPI() {

        // ── Reusable error response schemas ───────────────────────────────
        var errorSchema = new Schema<>()
                .type("object")
                .addProperty("success",   new Schema<>().type("boolean").example(false))
                .addProperty("status",    new Schema<>().type("integer").example(401))
                .addProperty("message",   new Schema<>().type("string").example("Unauthorized"))
                .addProperty("error",     new Schema<>().type("object")
                        .addProperty("code",   new Schema<>().type("string").example("UNAUTHORIZED"))
                        .addProperty("detail", new Schema<>().type("string")))
                .addProperty("path",      new Schema<>().type("string").example("/api/chat"))
                .addProperty("timestamp", new Schema<>().type("string").format("date-time"));

        var unauthorizedExample = new Example()
                .value("""
                        {
                          "success": false,
                          "status": 401,
                          "message": "Unauthorized",
                          "error": {
                            "code": "UNAUTHORIZED",
                            "detail": "Missing or invalid JWT token. Include 'Authorization: Bearer <token>' header."
                          },
                          "path": "/api/chat",
                          "timestamp": "2026-05-31T10:00:00Z"
                        }
                        """);

        var forbiddenExample = new Example()
                .value("""
                        {
                          "success": false,
                          "status": 403,
                          "message": "Forbidden",
                          "error": {
                            "code": "FORBIDDEN",
                            "detail": "You do not have permission to access this resource."
                          },
                          "path": "/actuator/env",
                          "timestamp": "2026-05-31T10:00:00Z"
                        }
                        """);

        var rateLimitExample = new Example()
                .value("""
                        {
                          "success": false,
                          "status": 429,
                          "message": "Too Many Requests",
                          "error": {
                            "code": "RATE_LIMITED",
                            "detail": "Rate limit exceeded. Please wait before sending another message."
                          },
                          "path": "/api/chat",
                          "timestamp": "2026-05-31T10:00:00Z"
                        }
                        """);

        // ── Reusable API responses ─────────────────────────────────────────
        var unauthorizedResponse = new ApiResponse()
                .description("Unauthorized — missing or invalid JWT token")
                .content(new Content().addMediaType(
                        "application/json",
                        new MediaType().schema(errorSchema)
                                .addExamples("example", unauthorizedExample)));

        var forbiddenResponse = new ApiResponse()
                .description("Forbidden — authenticated but insufficient role")
                .content(new Content().addMediaType(
                        "application/json",
                        new MediaType().schema(errorSchema)
                                .addExamples("example", forbiddenExample)));

        var rateLimitResponse = new ApiResponse()
                .description("Too Many Requests — Anthropic rate limit")
                .content(new Content().addMediaType(
                        "application/json",
                        new MediaType().schema(errorSchema)
                                .addExamples("example", rateLimitExample)));

        // ── Tags with full descriptions ────────────────────────────────────
        var authTag = new Tag()
                .name("Authentication")
                .description("""
                        **JWT Authentication endpoints.**
                        Register, login, refresh tokens, and view your profile.
                        Login endpoints are public — no token required.
                        """);

        var chatTag = new Tag()
                .name("Chat")
                .description("""
                        **Agentic AI Chat endpoints.** 🤖
                        Send messages to Claude. The model autonomously calls tools
                        (weather, orders, calculator, products) and returns a synthesised reply.
                        Requires `ROLE_USER` or `ROLE_ADMIN`.
                        
                        Supports both standard JSON responses and SSE streaming.
                        """);

        var healthTag = new Tag()
                .name("Health")
                .description("Application health check — public endpoint, no auth required.");

        return new OpenAPI()
                .tags(List.of(authTag, chatTag, healthTag))
                .components(new io.swagger.v3.oas.models.Components()
                        .addResponses("Unauthorized",  unauthorizedResponse)
                        .addResponses("Forbidden",     forbiddenResponse)
                        .addResponses("RateLimited",   rateLimitResponse));
    }
}