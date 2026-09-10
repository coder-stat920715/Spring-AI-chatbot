# 🤖 Spring AI Agentic Chatbot

<div align="center">

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.x-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
![Spring AI](https://img.shields.io/badge/Spring_AI-2.0-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Anthropic Claude](https://img.shields.io/badge/Anthropic-Claude_Sonnet_4-D97757?style=for-the-badge&logo=anthropic&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-Auth-000000?style=for-the-badge&logo=jsonwebtokens&logoColor=white)
![Coverage](https://img.shields.io/badge/Code_Coverage-97%25-brightgreen?style=for-the-badge&logo=jacoco&logoColor=white)

**A production-ready Spring Boot REST API powering an autonomous AI agent backed by Anthropic Claude.**  
The agent reasons over user messages and autonomously calls real tools — weather, order tracking, calculator, and product catalog — before synthesising a final reply.

[Quick Start](#-quick-start) • [Architecture](#-architecture) • [Agentic AI Flow](#-agentic-ai-flow-in-depth) • [API Reference](#-api-reference) • [Security](#-security-architecture) • [Configuration](#️-configuration)

</div>

---

## 📋 Table of Contents

- [Features](#-features)
- [Technology Stack](#-technology-stack)
- [Project Structure](#-project-structure)
- [Architecture](#-architecture)
- [Agentic AI Flow In Depth](#-agentic-ai-flow-in-depth)
- [JWT Authentication Flow](#-jwt-authentication-flow)
- [Security Architecture](#-security-architecture)
- [Agentic Tools](#-agentic-tools)
- [API Reference](#-api-reference)
- [Quick Start](#-quick-start)
- [Configuration](#️-configuration)
- [Code Coverage](#-code-coverage)
- [Production Notes](#-production-notes)

---

## ✨ Features

| Feature | Detail |
|---|---|
| 🧠 **Autonomous Agentic AI** | Claude decides which tools to call, executes them, and synthesises a final answer — zero hardcoding |
| 🔄 **Multi-turn Memory** | Conversation history persisted per `sessionId` via Spring AI `MessageWindowChatMemory` |
| 🌊 **SSE Streaming** | Token-by-token streaming via `text/event-stream` for real-time typing effects |
| 🔐 **JWT Auth** | Stateless access tokens (15 min) + refresh token rotation (7 days) |
| 🛡️ **RBAC** | Role-based access control (`ROLE_USER`, `ROLE_ADMIN`) enforced at URL and method level |
| 📖 **Swagger UI** | Interactive API docs auto-generated at `/swagger-ui.html` |
| 🔧 **4 Live Tools** | Weather, Order Tracking, Calculator, Product Info — all pluggable |
| 🌐 **Global Error Handling** | Every exception type maps to a consistent `ApiResponse` JSON shape |
| ✅ **97% Code Coverage** | Comprehensive JUnit 5 + Mockito test suite across all layers |
| 🐳 **Docker Ready** | Dockerfile + `docker-compose.yml` included |

---

## 🛠 Technology Stack

| Layer | Technology |
|---|---|
| **Runtime** | Java 21, Spring Boot 4.x |
| **AI Framework** | Spring AI 2.0 |
| **LLM** | Anthropic Claude (`claude-sonnet-4-20250514`) |
| **Security** | Spring Security 6, JWT (jjwt 0.12.x), BCrypt |
| **Web** | Spring MVC, Spring WebFlux (SSE streaming) |
| **Reactive** | Project Reactor (`Flux`) |
| **API Docs** | SpringDoc OpenAPI 3 / Swagger UI |
| **Expression Eval** | exp4j (safe arithmetic for CalculatorTool) |
| **Logging** | SLF4J + Logback |
| **Testing** | JUnit 5, Mockito, Spring Boot Test, JaCoCo |
| **Build** | Maven |
| **Infrastructure** | Docker, Terraform (EC2 + VPC modules) |

---

## 📁 Project Structure

```
spring-ai-chatbot/
├── src/
│   ├── main/java/com/agenticai/chatbot/
│   │   ├── advisor/
│   │   │   └── GlobalExceptionHandler.java     # Maps every exception → HTTP status
│   │   ├── config/
│   │   │   ├── AiConfig.java                   # ChatClient + ChatMemory beans
│   │   │   ├── OpenApiConfig.java              # Swagger UI / OpenAPI 3 setup
│   │   │   ├── PasswordConfig.java             # BCryptPasswordEncoder bean
│   │   │   ├── SecurityConfig.java             # Spring Security filter chain
│   │   │   └── WebConfig.java                  # CORS and MVC config
│   │   ├── controller/
│   │   │   ├── AuthController.java             # /api/auth/** — register, login, refresh, me
│   │   │   └── ChatController.java             # /api/chat/** — chat, stream, health, sessions
│   │   ├── model/
│   │   │   ├── ApiResponse.java                # Uniform response envelope
│   │   │   ├── ChatException.java              # Typed exception hierarchy
│   │   │   ├── ChatModels.java                 # Request/Reply records
│   │   │   └── auth/AuthModels.java            # Auth request/response records
│   │   ├── security/
│   │   │   ├── filter/
│   │   │   │   └── JwtAuthenticationFilter.java # Validates JWT on every request
│   │   │   ├── service/
│   │   │   │   ├── ChatbotUserDetailsService.java
│   │   │   │   └── UserStore.java              # In-memory ConcurrentHashMap user store
│   │   │   └── util/
│   │   │       └── JwtUtil.java                # Token generation, parsing, validation
│   │   ├── service/
│   │   │   └── ChatService.java                # Core agentic orchestration logic
│   │   └── tools/
│   │       ├── CalculatorTool.java             # @Tool — arithmetic evaluator
│   │       ├── OrderTrackingTool.java          # @Tool — order status lookup
│   │       ├── ProductInfoTool.java            # @Tool — product catalog
│   │       └── WeatherTool.java                # @Tool — weather by city
│   ├── main/resources/
│   │   └── application.yml                     # All configuration
│   └── test/                                   # 97% coverage test suite
├── terraform/                                  # EC2 + VPC + Security Groups IaC
├── Dockerfile
├── docker-compose.yml
├── docker-compose.test.yml
└── Jenkinsfile
```

---

## 🏛 Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         CLIENT (Postman / Browser / App)                │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   │  HTTPS
                                   ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     SPRING SECURITY FILTER CHAIN                        │
│                                                                         │
│   ┌──────────────────────────┐     ┌───────────────────────────────┐   │
│   │  JwtAuthenticationFilter │────▶│  SecurityContextHolder         │   │
│   │  (OncePerRequestFilter)  │     │  (sets Authentication object) │   │
│   └──────────────────────────┘     └───────────────────────────────┘   │
│                                                                         │
│   Public paths bypass JWT: /api/auth/login, /api/auth/register,        │
│   /api/auth/refresh, /swagger-ui/**, /v3/api-docs/**, /api/chat/health │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   │
                    ┌──────────────┴──────────────┐
                    ▼                             ▼
        ┌───────────────────┐         ┌───────────────────┐
        │  AuthController   │         │  ChatController    │
        │  /api/auth/**     │         │  /api/chat/**      │
        └────────┬──────────┘         └────────┬──────────┘
                 │                             │
                 ▼                             ▼
        ┌──────────────────┐        ┌────────────────────┐
        │  JwtUtil          │        │  ChatService        │
        │  UserStore        │        │  (Agentic Core)     │
        └──────────────────┘        └────────┬───────────┘
                                             │
                                   ┌─────────▼──────────┐
                                   │   Spring AI         │
                                   │   ChatClient        │
                                   │ (MessageWindow      │
                                   │  ChatMemory)        │
                                   └─────────┬──────────┘
                                             │
                                   ┌─────────▼──────────┐
                                   │  Anthropic Claude   │
                                   │  claude-sonnet-4    │
                                   │  (Tool-calling LLM) │
                                   └─────────┬──────────┘
                              ┌──────────────┼──────────────┐
                              ▼              ▼              ▼
                    ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
                    │ WeatherTool  │ │ OrderTracking │ │ Calculator   │
                    │ @Tool        │ │ Tool @Tool    │ │ Tool @Tool   │
                    └──────────────┘ └──────────────┘ └──────────────┘
                                   ┌──────────────┐
                                   │ ProductInfo  │
                                   │ Tool @Tool   │
                                   └──────────────┘
```

---

## 🧠 Agentic AI Flow In Depth

This is the heart of the application. Understanding this flow explains how Claude autonomously calls tools and returns an intelligent final answer.

### Step-by-Step: What happens when you POST `/api/chat`

```
USER                ChatController        ChatService         Spring AI ChatClient
 │                        │                   │                      │
 │  POST /api/chat         │                   │                      │
 │  { "message": "What    │                   │                      │
 │    is the weather in   │                   │                      │
 │    Tokyo?",            │                   │                      │
 │    "sessionId": "x" }  │                   │                      │
 ├───────────────────────▶│                   │                      │
 │                        │  chatService      │                      │
 │                        │  .chat(request)   │                      │
 │                        ├──────────────────▶│                      │
 │                        │                   │  1. resolveSessionId │
 │                        │                   │     (generate UUID   │
 │                        │                   │     if blank)        │
 │                        │                   │                      │
 │                        │                   │  2. chatClient       │
 │                        │                   │     .prompt()        │
 │                        │                   │     .user(message)   │
 │                        │                   │     .tools(weather,  │
 │                        │                   │       orders, calc,  │
 │                        │                   │       products)      │
 │                        │                   │     .advisors(       │
 │                        │                   │       sessionId)     │
 │                        │                   ├─────────────────────▶│
 │                        │                   │                      │
 │                        │          ┌────────────────────────────────────────────────────────────┐
 │                        │          │               ANTHROPIC CLAUDE API                         │
 │                        │          │                                                            │
 │                        │          │  TURN 1 — Claude receives:                                 │
 │                        │          │  • User message: "What is the weather in Tokyo?"           │
 │                        │          │  • Conversation history (from MessageWindowChatMemory)     │
 │                        │          │  • Tool schemas: get_weather, track_order,                 │
 │                        │          │    calculate, get_product_info                             │
 │                        │          │                                                            │
 │                        │          │  Claude reasons: "User wants weather → call get_weather"   │
 │                        │          │                                                            │
 │                        │          │  Claude responds with TOOL_USE block:                      │
 │                        │          │  {                                                         │
 │                        │          │    "type": "tool_use",                                     │
 │                        │          │    "name": "get_weather",                                  │
 │                        │          │    "input": { "city": "Tokyo", "unit": "celsius" }         │
 │                        │          │  }                                                         │
 │                        │          └────────────────────────────────────────────────────────────┘
 │                        │                   │                      │
 │                        │                   │  Spring AI intercepts│
 │                        │                   │  tool_use block and  │
 │                        │                   │  invokes locally:    │
 │                        │                   │  WeatherTool         │
 │                        │                   │  .getWeather(        │
 │                        │                   │    "Tokyo","celsius")│
 │                        │                   │                      │
 │                        │          ┌────────────────────────────────────────────────────────────┐
 │                        │          │  WeatherTool returns:                                      │
 │                        │          │  WeatherResponse {                                         │
 │                        │          │    city: "Tokyo",                                          │
 │                        │          │    temperature: 27.0,                                      │
 │                        │          │    unit: "celsius",                                        │
 │                        │          │    condition: "Partly Cloudy",                             │
 │                        │          │    humidity: 72,                                           │
 │                        │          │    windSpeed: 14.3                                         │
 │                        │          │  }                                                         │
 │                        │          └────────────────────────────────────────────────────────────┘
 │                        │                   │                      │
 │                        │          ┌────────────────────────────────────────────────────────────┐
 │                        │          │  TURN 2 — Spring AI sends back to Claude:                  │
 │                        │          │  • Original message                                        │
 │                        │          │  • Claude's tool_use request                               │
 │                        │          │  • Tool result (WeatherResponse JSON)                      │
 │                        │          │                                                            │
 │                        │          │  Claude now has all info and generates final text:          │
 │                        │          │  "The current weather in Tokyo is 27.0°C,                  │
 │                        │          │   Partly Cloudy with 72% humidity and                      │
 │                        │          │   wind speeds of 14.3 km/h."                               │
 │                        │          └────────────────────────────────────────────────────────────┘
 │                        │                   │◀─────────────────────│
 │                        │                   │  ChatResponse        │
 │                        │                   │  (text + metadata)   │
 │                        │                   │                      │
 │                        │◀──────────────────│                      │
 │                        │  ChatReply {      │                      │
 │                        │   sessionId,      │                      │
 │                        │   reply,          │                      │
 │                        │   toolsUsed:      │                      │
 │                        │    ["get_weather"]│                      │
 │                        │   model           │                      │
 │                        │  }                │                      │
 │◀───────────────────────│                   │                      │
 │  200 OK                │                   │                      │
 │  ApiResponse<ChatReply>│                   │                      │
```

### Multi-Tool Agentic Scenario

When a user asks something that requires multiple tools (e.g. *"Track order ORD-1002 and also what's 18% tip on $85?"*), Claude performs multiple tool calls in sequence or parallel:

```
User: "Track order ORD-1002 and calculate 18% tip on $85"
         │
         ▼
   Claude (Turn 1)
   ┌──────────────────────────────────┐
   │ Decides to call TWO tools:       │
   │  1. track_order("ORD-1002")      │
   │  2. calculate("85 * 0.18")       │
   └──────────────────────────────────┘
         │
    ┌────┴────┐
    ▼         ▼
OrderTrackingTool   CalculatorTool
track_order(        calculate(
 "ORD-1002")         "85 * 0.18")
    │                    │
    ▼                    ▼
{status:"In Transit", {expression:"85 * 0.18",
 delivery: "June 5",   result: 15.3}
 carrier: "UPS"...}
    │                    │
    └────────┬───────────┘
             ▼
       Claude (Turn 2)
       Receives both tool results
       Synthesises final reply:
       "Your order ORD-1002 is In Transit via UPS,
        expected June 5. Also, an 18% tip on $85
        comes to $15.30 (total: $100.30)."
```

### SSE Streaming Flow

```
POST /api/chat/stream
       │
       ▼
 ChatService.stream()
       │
       ▼
 chatClient.stream().content()  ─── returns Flux<String>
       │
       ▼  (each token emitted as it arrives)
 data: The
 data:  current
 data:  weather
 data:  in
 data:  Tokyo
 data:  is
 data:  27°C
       │
       ▼
 SSE Connection closes when Flux completes
```

---

## 🔐 JWT Authentication Flow

### Complete Token Lifecycle

```
┌─────────────────────────────────────────────────────────────┐
│                    REGISTRATION / LOGIN                      │
└─────────────────────────────────────────────────────────────┘

  Client                  AuthController              JwtUtil
    │                          │                        │
    │  POST /api/auth/login    │                        │
    │  { username, password }  │                        │
    ├─────────────────────────▶│                        │
    │                          │  AuthenticationManager │
    │                          │  .authenticate(...)    │  ← BCrypt verify
    │                          │                        │
    │                          │  generateAccessToken() │
    │                          ├───────────────────────▶│
    │                          │  JWT Header.Payload.Sig│
    │                          │  sub: "user"           │
    │                          │  roles: ["ROLE_USER"]  │
    │                          │  type: "access"        │
    │                          │  exp: now + 15min      │
    │                          │◀───────────────────────│
    │                          │                        │
    │                          │  generateRefreshToken()│
    │                          ├───────────────────────▶│
    │                          │  sub: "user"           │
    │                          │  type: "refresh"       │
    │                          │  exp: now + 7days      │
    │                          │◀───────────────────────│
    │                          │                        │
    │◀─────────────────────────│                        │
    │  200 OK                  │                        │
    │  { accessToken,          │                        │
    │    refreshToken,         │                        │
    │    expiresIn: 900,       │                        │
    │    tokenType: "Bearer" } │                        │

┌─────────────────────────────────────────────────────────────┐
│              AUTHENTICATED REQUEST FLOW                      │
└─────────────────────────────────────────────────────────────┘

  Client            JwtAuthFilter         ChatController
    │                    │                      │
    │  POST /api/chat    │                      │
    │  Authorization:    │                      │
    │  Bearer eyJhbG...  │                      │
    ├───────────────────▶│                      │
    │                    │  shouldNotFilter()?  │
    │                    │  path = /api/chat    │
    │                    │  → NO, process it    │
    │                    │                      │
    │                    │  extractBearerToken()│
    │                    │  isTokenStructureValid() ← fast sig check
    │                    │  extractUsername()   │
    │                    │  loadUserByUsername()│
    │                    │  isTokenValid()      │ ← expiry + subject
    │                    │  setAuthentication() │ ← into SecurityContext
    │                    ├─────────────────────▶│
    │                    │                      │  @PreAuthorize
    │                    │                      │  hasAnyRole('USER','ADMIN')
    │                    │                      │  → PASS
    │◀───────────────────────────────────────────│
    │  200 OK + AI reply │                      │

┌─────────────────────────────────────────────────────────────┐
│                    TOKEN REFRESH FLOW                        │
└─────────────────────────────────────────────────────────────┘

  Client               AuthController             JwtUtil
    │                        │                       │
    │  POST /api/auth/refresh│                       │
    │  { refreshToken: "..." }                       │
    ├───────────────────────▶│                       │
    │                        │  isTokenStructureValid?        │
    │                        │  extractTokenType() == "refresh"?
    │                        │  extractUsername()             │
    │                        │  loadUserByUsername()          │
    │                        │  generateAccessToken() (NEW)   │
    │                        │  generateRefreshToken() (NEW)  │ ← rotation
    │◀───────────────────────│                       │
    │  200 OK                │                       │
    │  { new accessToken,    │                       │
    │    new refreshToken }  │                       │
```

### JWT Token Structure

```
Header  (Base64URL)          Payload (Base64URL)           Signature
┌──────────────────┐   ┌─────────────────────────────┐   ┌──────────┐
│ {                │   │ {                           │   │ HMAC-    │
│   "alg": "HS256",│   │   "sub": "admin",           │   │ SHA256   │
│   "typ": "JWT"   │   │   "roles":["ROLE_ADMIN",    │   │ signed   │
│ }                │   │           "ROLE_USER"],      │   │ with     │
└──────────────────┘   │   "type": "access",         │   │ secret   │
        .              │   "iat": 1748786400,         │   │ key      │
                       │   "exp": 1748787300          │   └──────────┘
                       │ }                           │
                       └─────────────────────────────┘
                                    .
```

---

## 🛡 Security Architecture

### Endpoint Access Matrix

| Endpoint | Method | Auth Required | Role |
|---|---|---|---|
| `/api/auth/register` | POST | ❌ Public | — |
| `/api/auth/login` | POST | ❌ Public | — |
| `/api/auth/refresh` | POST | ❌ Public | — |
| `/api/auth/me` | GET | ✅ JWT | USER or ADMIN |
| `/api/chat` | POST | ✅ JWT | USER or ADMIN |
| `/api/chat/stream` | POST | ✅ JWT | USER or ADMIN |
| `/api/chat/sessions/{id}` | DELETE | ✅ JWT | USER or ADMIN |
| `/api/chat/health` | GET | ❌ Public | — |
| `/swagger-ui/**` | GET | ❌ Public | — |
| `/v3/api-docs/**` | GET | ❌ Public | — |
| `/actuator/health` | GET | ❌ Public | — |
| `/actuator/**` | ALL | ✅ JWT | ADMIN only |
| Everything else | ALL | ✅ JWT | ADMIN only |

### Security Filter Chain Order

```
Incoming Request
       │
       ▼
  ┌─────────────────────────────────┐
  │  CSRF Filter (DISABLED)         │  ← Correct for stateless REST
  └─────────────────────────────────┘
       │
       ▼
  ┌─────────────────────────────────┐
  │  JwtAuthenticationFilter        │
  │  (OncePerRequestFilter)         │
  │  • shouldNotFilter() check      │
  │  • Extract Bearer token         │
  │  • Validate structure           │
  │  • Load UserDetails             │
  │  • Set SecurityContext          │
  └─────────────────────────────────┘
       │
       ▼
  ┌─────────────────────────────────┐
  │  UsernamePasswordAuthFilter     │  ← Standard Spring Security
  └─────────────────────────────────┘
       │
       ▼
  ┌─────────────────────────────────┐
  │  AuthorizationFilter            │
  │  • URL-level rules              │
  │  • @PreAuthorize method rules   │
  └─────────────────────────────────┘
       │
  ┌────┴────┐
  ▼         ▼
PASS      FAIL
  │         │
Controller  │
           401 (no/bad token)
           403 (wrong role)
```

---

## 🔧 Agentic Tools

All tools are `@Component` classes with `@Tool`-annotated methods. Spring AI registers them with Claude automatically.

### `WeatherTool` — `get_weather`

```java
@Tool(name = "get_weather",
      description = "Get the current weather conditions for any city worldwide...")
public WeatherResponse getWeather(String city, String unit)
```

| Trigger Examples | Claude calls |
|---|---|
| "What's the weather in Dubai?" | `get_weather("Dubai", "celsius")` |
| "Is it raining in London?" | `get_weather("London", "celsius")` |
| "Temperature in NYC in Fahrenheit" | `get_weather("New York", "fahrenheit")` |

**Supported cities:** London, New York, Tokyo, Sydney, Paris, Dubai, Mumbai, Singapore + any city (defaults to generic range).

### `OrderTrackingTool` — `track_order`

```java
@Tool(name = "track_order",
      description = "Track the current status and location of a customer order...")
public OrderTrackingResponse trackOrder(String orderId)
```

| Order ID | Status | Carrier |
|---|---|---|
| ORD-1001 | Delivered | FedEx |
| ORD-1002 | In Transit | UPS |
| ORD-1003 | Processing | DHL |
| ORD-1004 | Out for Delivery | USPS |
| ORD-1005 | Shipped | FedEx |

### `CalculatorTool` — `calculate`

```java
@Tool(name = "calculate",
      description = "Evaluate arithmetic expressions — percentages, parentheses, exponents...")
public CalculatorResponse calculate(String expression)
```

Uses **exp4j** for safe, sandboxed expression evaluation. Whitelist-guarded: only digits, operators, dots, spaces, and parentheses allowed.

| Trigger Examples | Expression Evaluated |
|---|---|
| "What's 15% tip on $128.50?" | `128.50 * 0.15` |
| "Split $240 three ways" | `240 / 3` |
| "What is (45 + 32) * 2?" | `(45 + 32) * 2` |

### `ProductInfoTool` — `get_product_info`

```java
@Tool(name = "get_product_info",
      description = "Retrieve product details — name, price, availability, description...")
public ProductInfoResponse getProductInfo(String productId)
```

| Product ID | Name | Price | In Stock |
|---|---|---|---|
| PROD-001 | AcmePro Laptop 15" | $1,299.99 | ✅ |
| PROD-002 | AcmePhone X12 | $799.00 | ✅ |
| PROD-003 | AcmeBuds Pro | $149.99 | ❌ |
| PROD-004 | AcmeWatch Series 5 | $349.00 | ✅ |
| PROD-005 | AcmeTab 11 | $499.99 | ✅ |

---

## 📡 API Reference

### Base URL

```
http://localhost:8081
```

### Uniform Response Envelope

Every endpoint returns this shape:

```json
{
  "success": true,
  "status": 200,
  "message": "OK",
  "data": { ... },
  "error": null,
  "path": "/api/chat",
  "timestamp": "2026-06-01T10:00:00Z"
}
```

### Authentication Endpoints

#### `POST /api/auth/register`

```json
// Request
{
  "username": "alice",
  "password": "Alice@1234",
  "email": "alice@example.com",
  "role": "USER"
}

// Response 201
{
  "success": true,
  "status": 201,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "username": "alice",
    "roles": ["ROLE_USER"]
  }
}
```

#### `POST /api/auth/login`

```json
// Request
{
  "username": "admin",
  "password": "Admin@1234"
}

// Response 200 — same AuthResponse shape as register
```

**Demo credentials:**

| Username | Password | Roles |
|---|---|---|
| `admin` | `Admin@1234` | ROLE_ADMIN + ROLE_USER |
| `user` | `User@1234` | ROLE_USER |

#### `POST /api/auth/refresh`

```json
// Request
{ "refreshToken": "eyJhbGciOiJIUzI1NiJ9..." }

// Response 200 — new access + refresh tokens (rotation)
```

#### `GET /api/auth/me`

```
Authorization: Bearer <accessToken>
```

```json
// Response 200
{
  "data": {
    "username": "admin",
    "email": "N/A",
    "roles": ["ROLE_ADMIN", "ROLE_USER"],
    "active": true
  }
}
```

### Chat Endpoints

All chat endpoints require: `Authorization: Bearer <accessToken>`

#### `POST /api/chat` — Standard Chat

```json
// Request
{
  "message": "What is the weather in Tokyo?",
  "sessionId": "my-session-001"
}

// Response 200
{
  "data": {
    "sessionId": "my-session-001",
    "reply": "The current weather in Tokyo is 27.0°C, Partly Cloudy with 72% humidity and wind at 14.3 km/h.",
    "toolsUsed": ["get_weather"],
    "model": "claude-sonnet-4-20250514",
    "timestamp": "2026-06-01T10:00:00Z"
  }
}
```

> 💡 **Omit `sessionId`** to start a fresh conversation — a UUID is auto-generated and returned.

#### `POST /api/chat/stream` — SSE Streaming

```
Content-Type: application/json
Accept: text/event-stream
Authorization: Bearer <accessToken>
```

```
// Response stream
data: The
data:  current
data:  weather
data:  in
data:  Tokyo
data:  is
data:  27°C,
data:  Partly
data:  Cloudy.
```

#### `DELETE /api/chat/sessions/{sessionId}` — Clear History

```
DELETE /api/chat/sessions/my-session-001
Authorization: Bearer <accessToken>

// Response 204 No Content
```

#### `GET /api/chat/health` — Health Check (Public)

```json
{
  "data": {
    "status": "UP",
    "service": "spring-ai-chatbot",
    "version": "1.0.0"
  }
}
```

### Error Response Examples

| Status | Code | Scenario |
|---|---|---|
| `400` | `VALIDATION_ERROR` | Blank message or message > 4000 chars |
| `401` | `UNAUTHORIZED` | Missing or expired JWT token |
| `403` | `FORBIDDEN` | Valid JWT but insufficient role |
| `404` | `NOT_FOUND` | Unknown endpoint |
| `405` | `METHOD_NOT_ALLOWED` | Wrong HTTP method |
| `415` | `UNSUPPORTED_MEDIA_TYPE` | Non-JSON Content-Type |
| `429` | `RATE_LIMITED` | Anthropic rate limit hit |
| `502` | `BAD_GATEWAY` | Upstream Anthropic API error |
| `503` | `SERVICE_UNAVAILABLE` | AI service down |
| `504` | `GATEWAY_TIMEOUT` | Model response timed out |

---

## 🚀 Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+
- Anthropic API key → [console.anthropic.com](https://console.anthropic.com)

### 1. Clone & Configure

```bash
git clone https://github.com/souptiktat/spring-ai-chatbot.git
cd spring-ai-chatbot
```

Set environment variables (never hardcode secrets):

```bash
export ANTHROPIC_API_KEY=sk-ant-...
export JWT_SECRET=$(openssl rand -base64 32)
```

### 2. Run Locally

```bash
./mvnw spring-boot:run
```

The app starts on **port 8081**.

### 3. Run with Docker

```bash
docker-compose up --build
```

### 4. Explore the API

Open **Swagger UI**: [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)

**Auth flow in Swagger UI:**
1. Expand `POST /api/auth/login` → click **Try it out**
2. Enter `{ "username": "admin", "password": "Admin@1234" }`
3. Copy the `accessToken` from the response
4. Click the **🔒 Authorize** button at the top
5. Paste the token → click **Authorize**
6. All subsequent requests will include the JWT automatically

### 5. Try the Agentic Chat

```bash
# Login
TOKEN=$(curl -s -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@1234"}' \
  | jq -r '.data.accessToken')

# Ask a weather question (Claude will call get_weather autonomously)
curl -X POST http://localhost:8081/api/chat \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "message": "What is the weather in Tokyo?",
    "sessionId": "demo-1"
  }'

# Multi-tool question (Claude calls track_order AND calculate)
curl -X POST http://localhost:8081/api/chat \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "message": "Track order ORD-1002 and calculate 18% tip on $85",
    "sessionId": "demo-1"
  }'

# Follow-up (memory retained via sessionId)
curl -X POST http://localhost:8081/api/chat \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "message": "What was the order status you just told me?",
    "sessionId": "demo-1"
  }'
```

### 6. Run Tests

```bash
./mvnw test

# With coverage report
./mvnw test jacoco:report
# Open: target/site/jacoco/index.html
```

---

## ⚙️ Configuration

All configuration lives in `src/main/resources/application.yml`:

```yaml
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}           # Required — set as env var
      chat:
        options:
          model: claude-sonnet-4-20250514     # Claude model to use
          temperature: 0.7                    # Creativity (0=deterministic, 1=creative)
          max-tokens: 2048                    # Max tokens per response
          top-p: 0.9

    retry:
      max-attempts: 3                         # Retry on 429/529
      initial-interval: 2s
      on-http-codes: 429, 529                 # Anthropic rate limit codes

app:
  jwt:
    secret: ${JWT_SECRET}                     # Required — 256-bit Base64 secret
    access-token-expiration-ms: 900000        # 15 minutes
    refresh-token-expiration-ms: 604800000    # 7 days

server:
  port: 8081
```

### Environment Variables

| Variable | Required | Description |
|---|---|---|
| `ANTHROPIC_API_KEY` | ✅ | Your Anthropic API key |
| `JWT_SECRET` | ✅ | Base64-encoded 256-bit secret (generate: `openssl rand -base64 32`) |

---

## 📊 Code Coverage

Achieved **97% overall** across the full codebase, verified with JaCoCo:

| Package | Class % | Method % | Line % | Branch % |
|---|---|---|---|---|
| `advisor` | 100% | 100% | 100% | 100% |
| `config` | 100% | 100% | 100% | 100% |
| `controller` | 100% | 100% | 100% | 90% |
| `model` | 95% | 100% | 100% | 88% |
| `security.filter` | 100% | 100% | 100% | 81% |
| `security.service` | 100% | 100% | 100% | 83% |
| `security.util` | 100% | 100% | 91% | 100% |
| `service` | 100% | 100% | 100% | 92% |
| `tools` | 100% | 100% | 100% | 100% |
| **Overall** | **97%** | **100%** | **99%** | **92%** |

Test classes mirror production structure exactly:

```
GlobalExceptionHandlerTest    → advisor layer
AiConfigTest, SecurityConfigTest, OpenApiConfigTest → config layer
AuthControllerTest, ChatControllerTest              → controller layer
AuthModelsTest, ApiResponseTest, ChatExceptionTest  → model layer
JwtAuthenticationFilterTest                         → security filter
ChatbotUserDetailsServiceTest, UserStoreTest        → security service
JwtUtilTest                                         → security util
ChatServiceTest                                     → service layer
CalculatorToolTest, OrderTrackingToolTest, ...      → tools layer
ChatbotApplicationTest                              → smoke test
```

---

## 🏭 Production Notes

### Replace In-Memory UserStore

```java
// ChatbotUserDetailsService.java — swap UserStore for JPA
@Service
public class ChatbotUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository; // JPA repo
    
    @Override
    public UserDetails loadUserByUsername(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException(username));
    }
}
```

### Replace Mock Tools with Real APIs

```java
// WeatherTool.java — swap mock for OpenWeatherMap
RestClient weatherClient = RestClient.create("https://api.openweathermap.org");
// OrderTrackingTool — connect to your logistics API
// ProductInfoTool — connect to your product database
```

### Security Hardening Checklist

- [ ] Rotate `JWT_SECRET` — never reuse across environments
- [ ] Store secrets in AWS Secrets Manager / HashiCorp Vault
- [ ] Enable HTTPS / TLS termination (ALB or nginx)
- [ ] Set `access-token-expiration-ms` per your threat model
- [ ] Add IP-based rate limiting (Spring Cloud Gateway or nginx)
- [ ] Enable Spring Security audit logging
- [ ] Implement token blacklisting for logout (Redis)
- [ ] Replace `UserStore` with a persistent database

### Infrastructure

Terraform modules included under `terraform/` for AWS deployment:

```
terraform/
├── modules/
│   ├── ec2/         # EC2 instance with user_data bootstrap
│   ├── security-groups/  # Inbound 8081, SSH, HTTPS
│   └── vpc/         # VPC, subnets, IGW
└── variables/
    ├── dev.tfvars
    ├── uat.tfvars
    ├── pre-prod.tfvars
    └── prod.tfvars
```

---

## 📜 License

[Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0)

---

<div align="center">

Built with ☕ Java, 🌱 Spring AI, and 🤖 Anthropic Claude

</div>
