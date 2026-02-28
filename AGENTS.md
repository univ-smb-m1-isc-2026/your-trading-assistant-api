# Agent Guidelines: Trading Assistant

This document provides instructions and guidelines for agentic coding agents working on the Trading Assistant repository. Adhering to these patterns ensures consistency and maintainability across the codebase.

## Agent Behavior & Pedagogical Rules
- **Pedagogical Focus:** Your primary goal is to ensure the user learns as much as possible and gains technical skills. Always provide clear, detailed explanations for your implementation choices, architectural patterns, and tool selections. You are an educator as much as an assistant; focus on the "why" behind every decision.
- **No Automatic Git Operations:** NEVER perform `git commit`, `git push`, or any other Git operations automatically. You must strictly wait for an explicit user request before executing any Git-related commands. This ensures the user remains in control of the project's history.
- **Proactive Testing Proposals:** After each feature addition or modification, you must propose a specific testing strategy. You must explain the different types of tests relevant to the change (e.g., Unit Tests for isolated logic, Integration Tests for API endpoints or database interactions) and wait for user confirmation before implementation.

## Project Overview
- **Stack:** Java 21, Spring Boot 4.0.2, Maven. Jackson 3.x (group ID `tools.jackson`, NOT `com.fasterxml.jackson`).
- **Architecture:** Standard Layered Architecture.
  - `config`: Security and infrastructure configuration (`SecurityConfig`, `ApplicationConfig`, `CorsConfig`, `JwtAuthenticationFilter`, `DevDataInitializer`).
  - `controller`: Web and REST endpoints (`AuthController`, `HelloController`, `AssetController`, `FavoriteController`).
  - `service`: Core business logic (`AccountService`, `JwtService`, `AssetService`, `AssetDataSyncService`, `AssetDataProvider` (interface with date-range signature), `HyperliquidAssetDataProvider`, `FavoriteService`).
  - `repository`: Data access layer (`AccountRepository`, `AssetRepository`, `AssetDailyValueRepository`, `AccountFavoriteAssetRepository` — all extend `JpaRepository`).
  - `entity`: JPA entities (`Account` implements `UserDetails`, `Role` enum, `Asset`, `AssetDailyValue`, `AssetSource`, `AccountFavoriteAsset`).
  - `dto`: Data Transfer Objects (`RegisterRequest`, `LoginRequest`, `AuthResponse`, `AssetSummaryResponse`, `CandleResponse`).
  - `exception`: Custom exceptions and global handler (`AssetNotFoundException`, `FavoriteAlreadyExistsException`, `FavoriteNotFoundException`, `GlobalExceptionHandler` with inner `record ErrorResponse`).
- **Domain:** A trading assistant application to manage assets, strategies, and market analysis.
- **Authentication:** Fully implemented. JWT-based stateless auth via `/auth/register` and `/auth/login`. All other endpoints require a valid Bearer token.
- **Asset API:** Implemented. `GET /assets` returns all assets with their latest price sorted alphabetically. `GET /assets/{symbol}/candles` returns 1 year of daily OHLCV candles for a given symbol (404 if unknown).

## Build, Lint, and Test Commands

### Build
- **Clean and compile:** `./mvnw clean compile`
- **Full build (including tests):** `./mvnw clean install`
- **Run locally:** `./mvnw spring-boot:run`
  - *Note:* Configured to use an in-memory H2 database by default.
- **Dependency Analysis:** `./mvnw dependency:tree`

### Test
- **Run all tests:** `./mvnw test`
- **Run a single test class:** `./mvnw test -Dtest=TradingAssistantApplicationTests`
- **Run a single test method:** `./mvnw test -Dtest=TradingAssistantApplicationTests#contextLoads`
- **Generate coverage report:** `./mvnw clean test jacoco:report`
  - Report generated at `target/site/jacoco/index.html`
  - *Note:* JaCoCo 0.8.12 emits `IllegalClassFormatException` warnings when run on Java 24 (class file major version 68). This is noise only — tests still pass and the report is generated correctly. See **Known Issues** below.
- **Current test count:** 149 tests, all passing.

### Linting and Quality
- **Check for dependency updates:** `./mvnw versions:display-plugin-updates`
- **Enforcer rules:** `./mvnw enforcer:enforce` (if configured in `pom.xml`).

## Code Style Guidelines

### Naming Conventions
- **Classes:** `PascalCase`. Suffix with role if applicable:
    - `Controller` for REST endpoints.
    - `Service` for business logic.
    - `Repository` for persistence interfaces.
    - `Config` or `Configuration` for Spring beans.
    - `Tests` for test classes.
- **Methods and Variables:** `camelCase`. Use descriptive names (e.g., `calculateReturn` instead of `calc`).
- **Constants:** `UPPER_SNAKE_CASE` (e.g., `MAX_RETRY_ATTEMPTS`).
- **Packages:** `lowercase.snake_case`. Root is `fr.info803.trading_assistant`.
- **Booleans:** Use prefixes like `is`, `has`, `can` (e.g., `isAuthenticated`, `hasBalance`).

### Imports
- Avoid wildcard imports (e.g., `import java.util.*;`).
- Group imports with a single blank line between groups:
  1. Static imports
  2. Standard Java packages (`java.*`, `javax.*`, `jakarta.*`)
  3. External libraries (`org.springframework.*`, etc.)
  4. Project classes (`fr.info803.trading_assistant.*`)

### Formatting
- **Indentation:** 4 spaces. No tabs.
- **Line Length:** Aim for 120 characters maximum.
- **Braces:** Same line for opening braces, new line for closing braces.

### Types and Logic
- **Explicit Types:** Use explicit types rather than `var` unless the type is obvious.
- **Optional:** Use `java.util.Optional` for values that may be missing. Avoid returning `null`.
- **Streams:** Use Java Streams for collection processing.

### Error Handling
- Use Spring's `@ControllerAdvice` for global exception handling.
- Throw custom runtime exceptions for business rule violations.
- Log using SLF4J (Lombok's `@Slf4j` is encouraged).

## Testing Practices
- **Frameworks:** JUnit 5, Mockito.
- **MockMvc:** Use `MockMvc` for testing controllers.
- **Test Names:** Use descriptive names that state the requirement (e.g., `shouldReturnHelloWorldOnRoot`).
- **Assertions:** Use AssertJ's fluent API if available, otherwise JUnit 5 assertions.
- **Test Organization:** Use `@Nested` inner classes to group related tests (e.g., `GenerationTests`, `ValidationTests`).
- **Injecting `@Value` in unit tests:** Use reflection to set private `@Value` fields when testing without Spring context (avoids the need for a full `@SpringBootTest`):
  ```java
  Field field = service.getClass().getDeclaredField("secretKey");
  field.setAccessible(true);
  field.set(service, "test-value");
  ```
- **Spying on package-private methods:** Methods that require stubbing in tests should be `package-private` (not `private`) so Mockito's `spy()` can intercept them. Mark them with a comment explaining why visibility is not `private`.
  ```java
  // package-private for testability via Mockito.spy()
  boolean isTokenExpired(String token) { ... }
  ```
- **Controller tests — use `standaloneSetup`, NOT `@WebMvcTest`:** `@WebMvcTest` loads `SecurityConfig → ApplicationConfig → AccountRepository` which is a repository not available in the web slice and causes context failure. Use `MockMvcBuilders.standaloneSetup()` instead:
  ```java
  MockMvc mockMvc = MockMvcBuilders
      .standaloneSetup(new AssetController(assetService))
      .setControllerAdvice(new GlobalExceptionHandler())
      .build();
  ```
- **Jackson 3.x date fields in MockMvc tests:** Do NOT manually configure `JavaTimeModule` — the `jackson-datatype-jsr310` artifact no longer exists in Jackson 3.x; Java time support is built into `jackson-databind`. For `LocalDate`/`LocalDateTime` fields in standalone MockMvc assertions, use `.exists()` rather than `.value("2026-02-27")` since the exact format depends on Spring Boot autoconfiguration.

## Infrastructure & Configuration
- **Database:** PostgreSQL driver included for production. H2 used for local development/testing.
- **Security:** Configured in `SecurityConfig`. Public routes: `/auth/**` (register & login), `/`, and `/dev/**`. All other endpoints require a valid Bearer JWT token. CSRF is disabled (stateless API). Sessions are STATELESS.
- **CORS:** Configured in `CorsConfig`. Allowed origin is read from `cors.allowed-origins` in `application-{profile}.yaml`.
- **Configuration:** Properties managed in `src/main/resources/application.yaml`.

## Development Environment
- **Run with dev profile (seeded data):** `./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"`
  - Seeds 5 assets (BTC, ETH, AERO, SAGA, MANTA) with **1 year (365 days)** of daily candles fetched from the Hyperliquid API via a single ranged request per asset.
- **H2 Console:** Available at `/h2-console` when the app is running.
- **Hot Reload:** `spring-boot-devtools` is included for faster development cycles.

## Manual curl testing (dev profile)

```bash
# 1. Register a user — note the field is "username", NOT "firstName"/"lastName"
curl -s -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123","username":"testuser"}'

# 2. Login and capture token
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# 3. List assets (sorted A→Z, with latest price)
curl -s http://localhost:8080/assets -H "Authorization: Bearer $TOKEN"

# 4. Candles for a known symbol (~365 OHLCV entries)
curl -s http://localhost:8080/assets/BTC/candles -H "Authorization: Bearer $TOKEN"

# 5. Unknown symbol → 404 with {"error":"Asset not found","symbol":"UNKNOWN","timestamp":"..."}
curl -s http://localhost:8080/assets/UNKNOWN/candles -H "Authorization: Bearer $TOKEN"
```

## Common Tasks for Agents
- **Adding a new Controller:**
  1. Create class in `fr.info803.trading_assistant.controller`.
  2. Annotate with `@RestController`.
  3. Update `SecurityConfig` if the endpoint needs specific permissions.
  4. Add unit tests in `src/test/java`.
- **Adding a new Service:**
  1. Create class in `fr.info803.trading_assistant.service`.
  2. Annotate with `@Service` and use `@RequiredArgsConstructor` for dependency injection.
  3. Add `@Slf4j` for logging.
  4. Methods that need to be stubbed in unit tests should be `package-private` (not `private`).
- **Adding a new DTO:**
  1. Create class in `fr.info803.trading_assistant.dto`.
  2. Use Lombok (`@Getter`, `@Setter`, `@Builder`) to reduce boilerplate.
  3. Add Jakarta validation annotations (`@NotBlank`, `@Email`, `@Size`) for request DTOs.
- **Modifying Security:**
  1. Edit `SecurityConfig.java` to update the `SecurityFilterChain`.

## Known Issues
- **JaCoCo 0.8.12 + Java 24 runtime:** JaCoCo 0.8.12 does not fully support Java 24 class files (major version 68). It emits `IllegalClassFormatException` warnings during instrumentation of JDK internal classes and Mockito-generated proxies. This is noise only — tests still pass and coverage reports are generated correctly. Workaround to suppress noise in a single test run: `./mvnw test -Dargline=""`.
