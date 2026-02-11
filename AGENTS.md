# Agent Guidelines: Trading Assistant

This document provides instructions and guidelines for agentic coding agents working on the Trading Assistant repository. Adhering to these patterns ensures consistency and maintainability across the codebase.

## Project Overview
- **Stack:** Java 21, Spring Boot 4.0+, Maven.
- **Architecture:** Standard Layered Architecture.
  - `config`: Security and infrastructure configuration.
  - `controller`: Web and REST endpoints (API).
  - `service`: Core business logic (to be implemented).
  - `repository`: Data access layer (to be implemented).
- **Domain:** A trading assistant application to manage assets, strategies, and market analysis.

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

## Infrastructure & Configuration
- **Database:** PostgreSQL driver included for production. H2 used for local development/testing.
- **Security:** Configured in `SecurityConfig`. Public access currently allowed for `/` and `/h2-console/**`.
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
- **Modifying Security:**
  1. Edit `SecurityConfig.java` to update the `SecurityFilterChain`.
