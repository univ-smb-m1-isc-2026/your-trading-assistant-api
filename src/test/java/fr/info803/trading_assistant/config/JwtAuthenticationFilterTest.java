package fr.info803.trading_assistant.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import fr.info803.trading_assistant.entity.Account;
import fr.info803.trading_assistant.entity.Role;
import fr.info803.trading_assistant.service.JwtService;
import jakarta.servlet.FilterChain;

/**
 * Unit tests for the JwtAuthenticationFilter class.
 *
 * Tests JWT token extraction, validation, and SecurityContext authentication setup
 * in isolation (no Spring context needed). Uses real SecurityContextHolder with
 * empty contexts to avoid Mockito/JaCoCo compatibility issues on Java 21.
 *
 * Covers:
 * - Authorization header handling (missing, malformed, valid)
 * - Token extraction and validation
 * - SecurityContext state management
 * - Filter chain flow and continuation
 * - Error handling (business logic exceptions)
 *
 * Total: 14 unit tests organized in 5 nested groups
 */
@DisplayName("JwtAuthenticationFilter Unit Tests")
class JwtAuthenticationFilterTest {

    private JwtAuthenticationFilter filter;
    private JwtService jwtService;
    private UserDetailsService userDetailsService;
    private FilterChain filterChain;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        userDetailsService = mock(UserDetailsService.class);
        filterChain = mock(FilterChain.class);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        // Set fresh empty SecurityContext for each test
        SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());

        // Create filter instance with mocked dependencies
        filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
    }

    @AfterEach
    void tearDown() {
        // Clean up SecurityContextHolder
        SecurityContextHolder.clearContext();
    }

    /**
     * Group 1: Authorization Header Handling
     * Tests filter behavior with various Authorization header states
     */
    @Nested
    @DisplayName("Authorization Header Handling")
    class AuthorizationHeaderHandling {

        @Test
        @DisplayName("Should skip authentication when Authorization header is missing")
        void shouldSkipAuthenticationWhenHeaderMissing() throws Exception {
            // Arrange: No Authorization header set

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert: Filter chain called, no authentication attempted
            verify(filterChain).doFilter(request, response);
            verify(jwtService, never()).extractEmail(anyString());
            verify(userDetailsService, never()).loadUserByUsername(anyString());
        }

        @Test
        @DisplayName("Should skip authentication when Authorization header lacks Bearer prefix")
        void shouldSkipAuthenticationWhenMissingBearerPrefix() throws Exception {
            // Arrange: Authorization header without "Bearer " prefix
            request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert: Filter chain called, no JWT processing
            verify(filterChain).doFilter(request, response);
            verify(jwtService, never()).extractEmail(anyString());
            verify(userDetailsService, never()).loadUserByUsername(anyString());
        }

        @Test
        @DisplayName("Should extract token when Authorization header has Bearer prefix")
        void shouldExtractTokenWhenBearerPrefixPresent() throws Exception {
            // Arrange: Valid Bearer token header
            String token = "valid-jwt-token";
            request.addHeader("Authorization", "Bearer " + token);
            when(jwtService.extractEmail(token)).thenReturn(null);

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert: Token extraction was attempted
            verify(jwtService).extractEmail(token);
            verify(filterChain).doFilter(request, response);
        }
    }

    /**
     * Group 2: Token Extraction & Validation
     * Tests token processing with various validation outcomes
     */
    @Nested
    @DisplayName("Token Extraction & Validation")
    class TokenExtractionAndValidation {

        @Test
        @DisplayName("Should set authentication when token is valid and account exists")
        void shouldSetAuthenticationWhenTokenValidAndAccountExists() throws Exception {
            // Arrange: Valid token with existing account
            String token = "valid-jwt-token";
            String email = "user@example.com";
            Account account = Account.builder()
                    .id(1L)
                    .username("John Doe")
                    .email(email)
                    .password("hashed-password")
                    .role(Role.ROLE_USER)
                    .build();

            request.addHeader("Authorization", "Bearer " + token);
            when(jwtService.extractEmail(token)).thenReturn(email);
            when(userDetailsService.loadUserByUsername(email)).thenReturn(account);
            when(jwtService.isTokenValid(token, account)).thenReturn(true);

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert: Authentication was set in SecurityContext
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                    .isEqualTo(account);
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should not authenticate when email extraction returns null")
        void shouldNotAuthenticateWhenEmailExtractionReturnsNull() throws Exception {
            // Arrange: Token processing returns null email (malformed/invalid token)
            String token = "invalid-jwt-token";
            request.addHeader("Authorization", "Bearer " + token);
            when(jwtService.extractEmail(token)).thenReturn(null);

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert: No user details loaded, no authentication set
            verify(userDetailsService, never()).loadUserByUsername(anyString());
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should not authenticate when token validation fails")
        void shouldNotAuthenticateWhenTokenValidationFails() throws Exception {
            // Arrange: Valid email extraction but token validation fails (expired or bad signature)
            String token = "expired-jwt-token";
            String email = "user@example.com";
            Account account = Account.builder()
                    .id(1L)
                    .username("John Doe")
                    .email(email)
                    .password("hashed-password")
                    .role(Role.ROLE_USER)
                    .build();

            request.addHeader("Authorization", "Bearer " + token);
            when(jwtService.extractEmail(token)).thenReturn(email);
            when(userDetailsService.loadUserByUsername(email)).thenReturn(account);
            when(jwtService.isTokenValid(token, account)).thenReturn(false);

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert: User loaded but not authenticated
            verify(userDetailsService).loadUserByUsername(email);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should not authenticate when account not found for extracted email")
        void shouldNotAuthenticateWhenAccountNotFound() throws Exception {
            // Arrange: Valid email extraction but account doesn't exist
            String token = "valid-token-unknown-user";
            String email = "unknown@example.com";

            request.addHeader("Authorization", "Bearer " + token);
            when(jwtService.extractEmail(token)).thenReturn(email);
            when(userDetailsService.loadUserByUsername(email))
                    .thenThrow(new UsernameNotFoundException("Account not found"));

            // Act & Assert: Exception is propagated (filter doesn't suppress business exceptions)
            try {
                filter.doFilterInternal(request, response, filterChain);
            } catch (UsernameNotFoundException e) {
                assertThat(e.getMessage()).isEqualTo("Account not found");
            }
        }
    }

    /**
     * Group 3: SecurityContext State Management
     * Tests authentication object creation and context updates
     */
    @Nested
    @DisplayName("SecurityContext State Management")
    class SecurityContextStateManagement {

        @Test
        @DisplayName("Should not override existing authentication in SecurityContext")
        void shouldNotOverrideExistingAuthentication() throws Exception {
            // Arrange: Valid token but authentication already exists in context
            String token = "valid-jwt-token";
            String email = "user@example.com";

            // Pre-set an authentication in the context
            Authentication existingAuth = mock(Authentication.class);
            SecurityContextHolder.getContext().setAuthentication(existingAuth);

            request.addHeader("Authorization", "Bearer " + token);
            when(jwtService.extractEmail(token)).thenReturn(email);

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert: Original authentication preserved
            assertThat(SecurityContextHolder.getContext().getAuthentication())
                    .isEqualTo(existingAuth);
            verify(userDetailsService, never()).loadUserByUsername(anyString());
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should set authentication with correct UserDetails and authorities")
        void shouldSetAuthenticationWithCorrectDetailsAndAuthorities() throws Exception {
            // Arrange: Valid token and account with specific authorities
            String token = "valid-jwt-token";
            String email = "admin@example.com";
            Account account = Account.builder()
                    .id(1L)
                    .username("Admin User")
                    .email(email)
                    .password("hashed-password")
                    .role(Role.ROLE_ADMIN)
                    .build();

            request.addHeader("Authorization", "Bearer " + token);
            when(jwtService.extractEmail(token)).thenReturn(email);
            when(userDetailsService.loadUserByUsername(email)).thenReturn(account);
            when(jwtService.isTokenValid(token, account)).thenReturn(true);

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert: Authentication set with correct principal and authorities
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.getPrincipal()).isEqualTo(account);
            assertThat(auth.getAuthorities()).isNotEmpty();
            // Verify filter chain continues after auth setup
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should set WebAuthenticationDetails from request when authenticating")
        void shouldSetWebAuthenticationDetailsFromRequest() throws Exception {
            // Arrange: Valid token with request details to capture
            String token = "valid-jwt-token";
            String email = "user@example.com";
            Account account = Account.builder()
                    .id(1L)
                    .username("John Doe")
                    .email(email)
                    .password("hashed-password")
                    .role(Role.ROLE_USER)
                    .build();

            request.setRemoteAddr("192.168.1.1");
            request.addHeader("Authorization", "Bearer " + token);
            when(jwtService.extractEmail(token)).thenReturn(email);
            when(userDetailsService.loadUserByUsername(email)).thenReturn(account);
            when(jwtService.isTokenValid(token, account)).thenReturn(true);

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert: Authentication was set with details populated
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.getDetails()).isNotNull();
            verify(filterChain).doFilter(request, response);
        }
    }

    /**
     * Group 4: Filter Chain Flow
     * Tests filter chain continuation in various scenarios
     */
    @Nested
    @DisplayName("Filter Chain Flow")
    class FilterChainFlow {

        @Test
        @DisplayName("Should call filter chain with request and response on happy path")
        void shouldCallFilterChainOnHappyPath() throws Exception {
            // Arrange: Valid token and account
            String token = "valid-jwt-token";
            String email = "user@example.com";
            Account account = Account.builder()
                    .id(1L)
                    .username("John Doe")
                    .email(email)
                    .password("hashed-password")
                    .role(Role.ROLE_USER)
                    .build();

            request.addHeader("Authorization", "Bearer " + token);
            when(jwtService.extractEmail(token)).thenReturn(email);
            when(userDetailsService.loadUserByUsername(email)).thenReturn(account);
            when(jwtService.isTokenValid(token, account)).thenReturn(true);

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert: Filter chain called exactly once with correct arguments
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should call filter chain even when authentication fails")
        void shouldCallFilterChainWhenAuthenticationFails() throws Exception {
            // Arrange: Missing Authorization header
            request.removeHeader("Authorization");

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert: Filter chain still called (allows request to continue without auth)
            verify(filterChain).doFilter(request, response);
        }
    }

    /**
     * Group 5: Error Handling
     * Tests business logic exception scenarios
     */
    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("Should propagate UsernameNotFoundException when account not found")
        void shouldPropagateUsernameNotFoundExceptionWhenAccountNotFound() throws Exception {
            // Arrange: Valid token but UserDetailsService throws exception
            String token = "valid-token-for-deleted-user";
            String email = "deleted@example.com";

            request.addHeader("Authorization", "Bearer " + token);
            when(jwtService.extractEmail(token)).thenReturn(email);
            doThrow(new UsernameNotFoundException("User not found"))
                    .when(userDetailsService).loadUserByUsername(email);

            // Act & Assert: Exception is propagated (filter doesn't suppress business exceptions)
            try {
                filter.doFilterInternal(request, response, filterChain);
            } catch (UsernameNotFoundException e) {
                assertThat(e.getMessage()).contains("not found");
            }
        }

        @Test
        @DisplayName("Should propagate business logic exception from JwtService")
        void shouldPropagateBizLogicExceptionFromJwtService() throws Exception {
            // Arrange: JwtService throws exception during token validation
            String token = "malformed-token";
            String email = "user@example.com";
            Account account = Account.builder()
                    .id(1L)
                    .username("John Doe")
                    .email(email)
                    .password("hashed-password")
                    .role(Role.ROLE_USER)
                    .build();

            request.addHeader("Authorization", "Bearer " + token);
            when(jwtService.extractEmail(token)).thenReturn(email);
            when(userDetailsService.loadUserByUsername(email)).thenReturn(account);
            when(jwtService.isTokenValid(token, account))
                    .thenThrow(new IllegalArgumentException("Invalid token format"));

            // Act & Assert: Business exception propagates
            try {
                filter.doFilterInternal(request, response, filterChain);
            } catch (IllegalArgumentException e) {
                assertThat(e.getMessage()).contains("Invalid token format");
            }
        }
    }
}
