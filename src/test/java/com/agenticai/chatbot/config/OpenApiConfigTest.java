package com.agenticai.chatbot.config;

import com.agenticai.chatbot.service.ChatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// ✅ FIX: Spring Boot 4.0 BREAKING CHANGE — @AutoConfigureMockMvc moved to a new package.
//
// Spring Boot 3.x (OLD — broken on Spring Boot 4):
//   import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
//
// Spring Boot 4.0+ (CORRECT):
//   import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
//
// Required dependency in pom.xml (test scope):
//   <dependency>
//       <groupId>org.springframework.boot</groupId>
//       <artifactId>spring-boot-starter-webmvc-test</artifactId>
//       <scope>test</scope>
//   </dependency>
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link OpenApiConfig}.
 *
 * <p>Verifies that Swagger UI and OpenAPI documentation endpoints are:
 * <ul>
 *   <li>Publicly accessible (no JWT required)</li>
 *   <li>Returning correct content types</li>
 *   <li>Including security scheme definitions</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc   // ✅ from org.springframework.boot.webmvc.test.autoconfigure
@DisplayName("OpenApiConfig — Swagger / OpenAPI endpoints")
class OpenApiConfigTest {

    @Autowired  private MockMvc mockMvc;
    @MockitoBean private ChatService chatService;

    @Test
    @DisplayName("GET /v3/api-docs should return OpenAPI JSON without auth")
    void openApiJsonIsPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("OpenAPI spec should include BearerAuth security scheme")
    void specIncludesBearerAuthScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.BearerAuth").exists())
                .andExpect(jsonPath("$.components.securitySchemes.BearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.BearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.BearerAuth.bearerFormat").value("JWT"));
    }

    @Test
    @DisplayName("OpenAPI spec should include ApiKeyAuth security scheme")
    void specIncludesApiKeyScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.ApiKeyAuth").exists());
    }

    @Test
    @DisplayName("OpenAPI spec should have correct API title")
    void specHasCorrectTitle() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("🤖 Spring AI Agentic Chatbot API"))
                .andExpect(jsonPath("$.info.version").value("1.0.0"));
    }

    @Test
    @DisplayName("OpenAPI spec should expose /api/auth/login endpoint")
    void specExposesLoginEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths./api/auth/login").exists());
    }

    @Test
    @DisplayName("OpenAPI spec should expose /api/chat endpoint")
    void specExposesChatEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths./api/chat").exists());
    }

    @Test
    @DisplayName("GET /swagger-ui.html should redirect to Swagger UI index (3xx)")
    void swaggerUiAccessible() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("GET /v3/api-docs.yaml should return YAML without auth")
    void openApiYamlIsPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("OpenAPI spec should list server URLs")
    void specHasServerUrls() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servers").isArray())
                .andExpect(jsonPath("$.servers[0].url").value("http://localhost:8080"));
    }

    @Test
    @DisplayName("OpenAPI spec should define global security requirement")
    void specHasGlobalSecurity() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.security").isArray());
    }
}