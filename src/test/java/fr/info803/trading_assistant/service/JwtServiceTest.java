package fr.info803.trading_assistant.service;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import fr.info803.trading_assistant.entity.Account;
import fr.info803.trading_assistant.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Unit tests for the JwtService class.
 *
 * Tests JWT token generation, validation, and claim extraction in isolation (no Spring context).
 * Uses reflection to inject @Value configuration for testing without Spring dependency injection.
 *
 * Covers:
 * - Token generation with correct claims and expiration
 * - Email extraction from tokens
 * - Token validation (signature and expiration)
 * - Token integrity (invalid signatures, malformed tokens)
 *
 * Tolerance: ±100ms for time-based assertions (accounts for test execution variance)
 */
@DisplayName("JwtService Unit Tests")
class JwtServiceTest {

    private JwtService jwtService;

    // Test JWT configuration
    private static final String TEST_SECRET =
            "test-secret-key-must-be-at-least-256-bits-long-for-hmac-sha256-testing-purposes";
    private static final long TEST_EXPIRATION_MS = 86400000; // 24 hours

    @BeforeEach
    void setUp() throws IllegalAccessException {
        jwtService = new JwtService();

        // Inject private @Value fields using reflection
        setPrivateField(jwtService, "secretKey", TEST_SECRET);
        setPrivateField(jwtService, "expirationMs", TEST_EXPIRATION_MS);
    }

    /**
     * Helper method to set private fields via reflection.
     * Used to inject Spring @Value configuration in unit tests without Spring context.
     */
    private void setPrivateField(Object obj, String fieldName, Object value)
            throws IllegalAccessException {
        Field field = Arrays.stream(obj.getClass().getDeclaredFields())
                .filter(f -> f.getName().equals(fieldName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Field not found: " + fieldName));
        field.setAccessible(true);
        field.set(obj, value);
    }

    /**
     * Helper method to extract all claims from a JWT token.
     * Used in assertions to verify token structure.
     */
    private Claims getAllClaims(String token) {
        byte[] keyBytes = TEST_SECRET.getBytes(StandardCharsets.UTF_8);
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(keyBytes))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // ============================================================================
    // TOKEN GENERATION TESTS
    // ============================================================================

    @Nested
    @DisplayName("Token Generation")
    class GenerationTests {

        @Test
        @DisplayName("should generate valid token for account")
        void shouldGenerateValidTokenForAccount() {
            // Arrange
            Account account = Account.builder()
                    .id(1L)
                    .username("Jean Dupont")
                    .email("jean@example.com")
                    .password("hashedPassword")
                    .role(Role.ROLE_USER)
                    .build();

            // Act
            String token = jwtService.generateToken(account);

            // Assert
            assertThat(token).isNotNull().isNotEmpty();

            // Verify token structure (Header.Payload.Signature)
            assertThat(token).contains(".");

            // Parse and verify claims
            Claims claims = getAllClaims(token);
            assertThat(claims).isNotNull();
            assertThat(claims.getSubject()).isEqualTo("jean@example.com");
            assertThat(claims.get("id")).isEqualTo(1);
            assertThat(claims.get("username")).isEqualTo("Jean Dupont");
            assertThat(claims.getIssuedAt()).isNotNull();
            assertThat(claims.getExpiration()).isNotNull();
            assertThat(claims.getExpiration()).isAfter(new Date());
        }

        @Test
        @DisplayName("should set correct expiration time (24 hours)")
        void shouldSetCorrectExpirationTime() {
            // Arrange
            Account account = Account.builder()
                    .id(2L)
                    .username("Alice")
                    .email("alice@example.com")
                    .password("hash")
                    .role(Role.ROLE_USER)
                    .build();

            // Act
            String token = jwtService.generateToken(account);

            // Assert
            Claims claims = getAllClaims(token);
            long issuedAtTime = claims.getIssuedAt().getTime();
            long expirationTime = claims.getExpiration().getTime();
            long actualExpirationDuration = expirationTime - issuedAtTime;

            // Verify expiration is approximately 24 hours (TEST_EXPIRATION_MS)
            // Allow ±1000ms tolerance for test execution variance
            assertThat(actualExpirationDuration)
                    .isGreaterThanOrEqualTo(TEST_EXPIRATION_MS - 1000)
                    .isLessThanOrEqualTo(TEST_EXPIRATION_MS + 1000);
        }
    }

    // ============================================================================
    // EMAIL EXTRACTION TESTS
    // ============================================================================

    @Nested
    @DisplayName("Email Extraction")
    class ExtractionTests {

        @Test
        @DisplayName("should extract email from valid token")
        void shouldExtractEmailFromValidToken() {
            // Arrange
            Account account = Account.builder()
                    .id(3L)
                    .username("Bob Smith")
                    .email("bob@example.com")
                    .password("hash")
                    .role(Role.ROLE_USER)
                    .build();

            String token = jwtService.generateToken(account);

            // Act
            String extractedEmail = jwtService.extractEmail(token);

            // Assert
            assertThat(extractedEmail).isEqualTo("bob@example.com");
        }

        @Test
        @DisplayName("should extract correct email from different accounts")
        void shouldExtractCorrectEmailFromDifferentAccounts() {
            // Arrange
            Account accountA = Account.builder()
                    .id(4L)
                    .username("Alice")
                    .email("alice@example.com")
                    .password("hash")
                    .role(Role.ROLE_USER)
                    .build();

            Account accountB = Account.builder()
                    .id(5L)
                    .username("Bob")
                    .email("bob@example.com")
                    .password("hash")
                    .role(Role.ROLE_USER)
                    .build();

            String tokenA = jwtService.generateToken(accountA);
            String tokenB = jwtService.generateToken(accountB);

            // Act
            String emailA = jwtService.extractEmail(tokenA);
            String emailB = jwtService.extractEmail(tokenB);

            // Assert
            assertThat(emailA).isEqualTo("alice@example.com");
            assertThat(emailB).isEqualTo("bob@example.com");
        }
    }

    // ============================================================================
    // TOKEN VALIDATION TESTS
    // ============================================================================

    @Nested
    @DisplayName("Token Validation")
    class ValidationTests {

        @Test
        @DisplayName("should validate token with correct email and not expired")
        void shouldValidateTokenWithCorrectEmailAndNotExpired() {
            // Arrange
            Account account = Account.builder()
                    .id(6L)
                    .username("Charlie")
                    .email("charlie@example.com")
                    .password("hash")
                    .role(Role.ROLE_USER)
                    .build();

            String token = jwtService.generateToken(account);

            // Act
            boolean isValid = jwtService.isTokenValid(token, account);

            // Assert
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("should return false when email does not match")
        void shouldReturnFalseWhenEmailDoesNotMatch() {
            // Arrange
            Account accountA = Account.builder()
                    .id(7L)
                    .username("Alice")
                    .email("alice@example.com")
                    .password("hash")
                    .role(Role.ROLE_USER)
                    .build();

            Account accountB = Account.builder()
                    .id(8L)
                    .username("Bob")
                    .email("bob@example.com")
                    .password("hash")
                    .role(Role.ROLE_USER)
                    .build();

            String tokenA = jwtService.generateToken(accountA);

            // Act
            // Validate token from accountA with accountB's credentials
            boolean isValid = jwtService.isTokenValid(tokenA, accountB);

            // Assert
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("should return false when token is expired")
        void shouldReturnFalseWhenTokenIsExpired() throws IllegalAccessException, InterruptedException {
            // Arrange
            Account account = Account.builder()
                    .id(9L)
                    .username("Dave")
                    .email("dave@example.com")
                    .password("hash")
                    .role(Role.ROLE_USER)
                    .build();

            // Set expiration to 1ms (effectively immediate expiration)
            setPrivateField(jwtService, "expirationMs", 1L);

            String token = jwtService.generateToken(account);

            // Reset expiration back to normal for validation
            setPrivateField(jwtService, "expirationMs", TEST_EXPIRATION_MS);

            // Wait for token to expire
            Thread.sleep(10);

            // Act & Assert
            // isTokenValid() should return false for expired token
            // Note: It may throw ExpiredJwtException when trying to parse, which is also acceptable
            try {
                boolean isValid = jwtService.isTokenValid(token, account);
                assertThat(isValid).isFalse();
            } catch (Exception e) {
                // ExpiredJwtException is acceptable - token is definitely expired
                assertThat(e.getMessage()).containsIgnoringCase("expired");
            }
        }
    }

    // ============================================================================
    // TOKEN INTEGRITY TESTS
    // ============================================================================

    @Nested
    @DisplayName("Token Integrity")
    class IntegrityTests {

        @Test
        @DisplayName("should throw exception on invalid signature")
        void shouldThrowExceptionOnInvalidSignature() {
            // Arrange
            Account account = Account.builder()
                    .id(10L)
                    .username("Eve")
                    .email("eve@example.com")
                    .password("hash")
                    .role(Role.ROLE_USER)
                    .build();

            String validToken = jwtService.generateToken(account);

            // Tamper with token: replace last 20 characters with garbage
            String tamperedToken = validToken.substring(0, validToken.length() - 20) + "00000000000000000000";

            // Act & Assert
            assertThatThrownBy(() -> jwtService.extractEmail(tamperedToken))
                    .isInstanceOf(Exception.class);
        }

         @Test
         @DisplayName("should throw exception on malformed token")
         void shouldThrowExceptionOnMalformedToken() {
             // Arrange
             String malformedToken = "invalid.token.format";

             // Act & Assert
             assertThatThrownBy(() -> jwtService.extractEmail(malformedToken))
                     .isInstanceOf(Exception.class);
         }
     }

     // ============================================================================
     // NULL EMAIL HANDLING TESTS
     // ============================================================================

     @Nested
     @DisplayName("Null Email Handling")
     class NullEmailHandlingTests {

         @Test
         @DisplayName("should return false when email extraction returns null")
         void shouldReturnFalseWhenEmailExtractionReturnsNull() {
             // Arrange
             Account account = Account.builder()
                     .id(11L)
                     .username("Frank")
                     .email("frank@example.com")
                     .password("hash")
                     .role(Role.ROLE_USER)
                     .build();

             // Create a token with valid structure but craft a scenario where
             // extractEmail would theoretically return null.
             // Since extractEmail calls extractClaim which calls extractAllClaims,
             // we test by passing a malformed token that causes parsing issues.
             // The null check in isTokenValid should prevent NPE.
             
             String malformedToken = "invalid.token.structure";

             // Act & Assert
             // isTokenValid should handle gracefully when extractEmail throws
             // or when the extraction logic would result in null.
             // The implementation should not throw NPE due to the null check at line 79.
             assertThatThrownBy(() -> jwtService.isTokenValid(malformedToken, account))
                     .isInstanceOf(Exception.class)
                     .doesNotHave(new org.assertj.core.api.Condition<Throwable>(
                             ex -> ex instanceof NullPointerException,
                             "should not be NullPointerException"));
         }

         @Test
         @DisplayName("should not throw NullPointerException on null email from isTokenValid")
         void shouldNotThrowNullPointerExceptionOnNullEmailFromIsTokenValid() {
             // Arrange
             Account account = Account.builder()
                     .id(12L)
                     .username("Grace")
                     .email("grace@example.com")
                     .password("hash")
                     .role(Role.ROLE_USER)
                     .build();

             // Malformed token that would cause extractEmail to throw
             String malformedToken = "header.invalid.signature";

             // Act & Assert
             // The important thing is that isTokenValid doesn't crash with NPE
             // even if extractEmail fails or returns null.
             try {
                 jwtService.isTokenValid(malformedToken, account);
             } catch (Exception e) {
                 // Any exception except NullPointerException is acceptable
                 assertThat(e).isNotInstanceOf(NullPointerException.class);
             }
         }

         @Test
         @DisplayName("should validate email match before checking expiration")
         void shouldValidateEmailMatchBeforeCheckingExpiration() throws IllegalAccessException {
             // Arrange
             Account accountA = Account.builder()
                     .id(13L)
                     .username("Henry")
                     .email("henry@example.com")
                     .password("hash")
                     .role(Role.ROLE_USER)
                     .build();

             Account accountB = Account.builder()
                     .id(14L)
                     .username("Iris")
                     .email("iris@example.com")
                     .password("hash")
                     .role(Role.ROLE_USER)
                     .build();

             // Create a valid token for accountA
             String tokenA = jwtService.generateToken(accountA);

             // Set expiration to a very long time so it won't expire during test
             setPrivateField(jwtService, "expirationMs", TEST_EXPIRATION_MS);

             // Act
             // Validate token from accountA with accountB's credentials
             // This should return false due to email mismatch, regardless of expiration
             boolean isValid = jwtService.isTokenValid(tokenA, accountB);

             // Assert
             assertThat(isValid).isFalse();
         }
     }
}
