# Agent Guidelines: Trading Assistant

This document provides instructions and guidelines for agentic coding agents working on the Trading Assistant repository. Adhering to these patterns ensures consistency and maintainability across the codebase.

## Agent Behavior & Pedagogical Rules
- **Pedagogical Focus:** Your primary goal is to ensure the user learns as much as possible and gains technical skills. Always provide clear, detailed explanations for your implementation choices, architectural patterns, and tool selections. You are an educator as much as an assistant; focus on the "why" behind every decision.
- **No Automatic Git Operations:** NEVER perform `git commit`, `git push`, or any other Git operations automatically. You must strictly wait for an explicit user request before executing any Git-related commands. This ensures the user remains in control of the project's history.
- **Proactive Testing Proposals:** After each feature addition or modification, you must propose a specific testing strategy. You must explain the different types of tests relevant to the change (e.g., Unit Tests for isolated logic, Integration Tests for API endpoints or database interactions) and wait for user confirmation before implementation.

## Project Overview
- **Stack:** Java 21, Spring Boot 4.0+, Maven.
- **Architecture:** Standard Layered Architecture.
  - `config`: Security and infrastructure configuration (`SecurityConfig`, `ApplicationConfig`, `CorsConfig`, `JwtAuthenticationFilter`).
  - `controller`: Web and REST endpoints (e.g., `AuthController`, `HelloController`).
  - `service`: Core business logic (e.g., `AccountService`, `JwtService`).
  - `repository`: Data access layer (e.g., `AccountRepository` extends `JpaRepository`).
  - `entity`: JPA entities (e.g., `Account` implements `UserDetails`, `Role` enum).
  - `dto`: Data Transfer Objects for request/response (e.g., `RegisterRequest`, `LoginRequest`, `AuthResponse`).
- **Domain:** A trading assistant application to manage assets, strategies, and market analysis.
- **Authentication:** Fully implemented. JWT-based stateless auth via `/auth/register` and `/auth/login`. All other endpoints require a valid Bearer token.

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

## Infrastructure & Configuration
- **Database:** PostgreSQL driver included for production. H2 used for local development/testing.
- **Security:** Configured in `SecurityConfig`. Public routes: `/auth/**` (register & login) and `/`. All other endpoints require a valid Bearer JWT token. CSRF is disabled (stateless API). Sessions are STATELESS.
- **CORS:** Configured in `CorsConfig`. Allowed origin is read from `cors.allowed-origins` in `application-{profile}.yaml`.
- **Configuration:** Properties managed in `src/main/resources/application.yaml`.

## Development Environment
- **H2 Console:** Available at `/h2-console` when the app is running.
- **Hot Reload:** `spring-boot-devtools` is included for faster development cycles.

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
