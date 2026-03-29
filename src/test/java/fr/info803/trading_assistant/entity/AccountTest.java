package fr.info803.trading_assistant.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Account entity.
 * 
 * Tests the Account JPA entity in isolation (no Spring context).
 * Covers:
 * - Lombok-generated constructors and builders
 * - UserDetails interface contract (Spring Security)
 * - Custom business methods
 */
@DisplayName("Account Entity Unit Tests")
class AccountTest {

    @Nested
    @DisplayName("Constructor and Builder")
    class ConstructorAndBuilderTests {

        @Test
        @DisplayName("should create Account with builder")
        void testBuilderWithAllFields() {
            // Arrange & Act
            Account account = Account.builder()
                    .id(1L)
                    .username("Jean Dupont")
                    .email("jean@example.com")
                    .password("hashed_password")
                    .role(Role.ROLE_USER)
                    .build();

            // Assert
            assertThat(account)
                    .isNotNull()
                    .satisfies(a -> {
                        assertThat(a.getId()).isEqualTo(1L);
                        assertThat(a.getUsername()).isEqualTo("jean@example.com");
                        assertThat(a.getDisplayUsername()).isEqualTo("Jean Dupont");
                        assertThat(a.getPassword()).isEqualTo("hashed_password");
                        assertThat(a.getRole()).isEqualTo(Role.ROLE_USER);
                    });
        }

        @Test
        @DisplayName("should create Account with no-args constructor")
        void testNoArgsConstructor() {
            // Arrange & Act
            Account account = new Account();

            // Assert
            assertThat(account).isNotNull();
            assertThat(account.getId()).isNull();
            assertThat(account.getUsername()).isNull();
            assertThat(account.getEmail()).isNull();
        }

        @Test
        @DisplayName("should create Account with all-args constructor")
        void testAllArgsConstructor() {
            // Arrange & Act
            Account account = new Account(
                    1L,
                    "Jean",
                    "jean@example.com",
                    "password123",
                    Role.ROLE_ADMIN,
                    "webhook"
            );

            // Assert
            assertThat(account.getId()).isEqualTo(1L);
            assertThat(account.getDisplayUsername()).isEqualTo("Jean");
            assertThat(account.getUsername()).isEqualTo("jean@example.com");
            assertThat(account.getPassword()).isEqualTo("password123");
            assertThat(account.getRole()).isEqualTo(Role.ROLE_ADMIN);
            assertThat(account.getDiscordWebhook()).isEqualTo("webhook");
        }

        @Test
        @DisplayName("should support setter methods")
        void testSetters() {
            // Arrange
            Account account = new Account();

            // Act
            account.setId(5L);
            account.setUsername("Marie");
            account.setEmail("marie@example.com");
            account.setPassword("hash123");
            account.setRole(Role.ROLE_ADMIN);
            account.setDiscordWebhook("webhook");

            // Assert
            assertThat(account.getId()).isEqualTo(5L);
            assertThat(account.getDisplayUsername()).isEqualTo("Marie");
            assertThat(account.getUsername()).isEqualTo("marie@example.com");
            assertThat(account.getPassword()).isEqualTo("hash123");
            assertThat(account.getRole()).isEqualTo(Role.ROLE_ADMIN);
            assertThat(account.getDiscordWebhook()).isEqualTo("webhook");
        }
    }

    @Nested
    @DisplayName("UserDetails Contract")
    class UserDetailsContractTests {

        @Test
        @DisplayName("getUsername() should return email (Spring Security identifier)")
        void testGetUsernameReturnsEmail() {
            // Arrange
            Account account = Account.builder()
                    .username("Display Name")
                    .email("user@example.com")
                    .password("hashed")
                    .role(Role.ROLE_USER)
                    .build();

            // Act
            String username = account.getUsername();

            // Assert
            assertThat(username).isEqualTo("user@example.com");
        }

        @Test
        @DisplayName("getPassword() should return the hashed password")
        void testGetPassword() {
            // Arrange
            String hashedPassword = "$2a$10$slYQmyNdGzin7olVVCb1Be7DlH.PKZbv5H8KnzzVgXXbVxzy72uDm";
            Account account = Account.builder()
                    .email("jean@example.com")
                    .password(hashedPassword)
                    .username("Jean")
                    .role(Role.ROLE_USER)
                    .build();

            // Act
            String password = account.getPassword();

            // Assert
            assertThat(password).isEqualTo(hashedPassword);
        }

        @Test
        @DisplayName("getAuthorities() should return ROLE_USER authority")
        void testGetAuthoritiesForUserRole() {
            // Arrange
            Account account = Account.builder()
                    .email("user@example.com")
                    .username("User")
                    .password("hash")
                    .role(Role.ROLE_USER)
                    .build();

            // Act
            Collection<? extends GrantedAuthority> authorities = account.getAuthorities();

            // Assert
            assertThat(authorities)
                    .hasSize(1)
                    .extracting("authority")
                    .containsExactly("ROLE_USER");
        }

        @Test
        @DisplayName("getAuthorities() should return ROLE_ADMIN authority")
        void testGetAuthoritiesForAdminRole() {
            // Arrange
            Account account = Account.builder()
                    .email("admin@example.com")
                    .username("Admin")
                    .password("hash")
                    .role(Role.ROLE_ADMIN)
                    .build();

            // Act
            Collection<? extends GrantedAuthority> authorities = account.getAuthorities();

            // Assert
            assertThat(authorities)
                    .hasSize(1)
                    .extracting("authority")
                    .containsExactly("ROLE_ADMIN");
        }

        @Test
        @DisplayName("isAccountNonExpired() should return true")
        void testIsAccountNonExpired() {
            // Arrange
            Account account = Account.builder()
                    .email("user@example.com")
                    .username("User")
                    .password("hash")
                    .role(Role.ROLE_USER)
                    .build();

            // Act & Assert
            assertThat(account.isAccountNonExpired()).isTrue();
        }

        @Test
        @DisplayName("isAccountNonLocked() should return true")
        void testIsAccountNonLocked() {
            // Arrange
            Account account = Account.builder()
                    .email("user@example.com")
                    .username("User")
                    .password("hash")
                    .role(Role.ROLE_USER)
                    .build();

            // Act & Assert
            assertThat(account.isAccountNonLocked()).isTrue();
        }

        @Test
        @DisplayName("isCredentialsNonExpired() should return true")
        void testIsCredentialsNonExpired() {
            // Arrange
            Account account = Account.builder()
                    .email("user@example.com")
                    .username("User")
                    .password("hash")
                    .role(Role.ROLE_USER)
                    .build();

            // Act & Assert
            assertThat(account.isCredentialsNonExpired()).isTrue();
        }

        @Test
        @DisplayName("isEnabled() should return true")
        void testIsEnabled() {
            // Arrange
            Account account = Account.builder()
                    .email("user@example.com")
                    .username("User")
                    .password("hash")
                    .role(Role.ROLE_USER)
                    .build();

            // Act & Assert
            assertThat(account.isEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("Custom Methods")
    class CustomMethodsTests {

        @Test
        @DisplayName("getDisplayUsername() should return the username field")
        void testGetDisplayUsername() {
            // Arrange
            Account account = Account.builder()
                    .username("Jean Dupont")
                    .email("jean@example.com")
                    .password("hash")
                    .role(Role.ROLE_USER)
                    .build();

            // Act
            String displayUsername = account.getDisplayUsername();

            // Assert
            assertThat(displayUsername).isEqualTo("Jean Dupont");
        }

        @Test
        @DisplayName("getDisplayUsername() should differ from getUsername()")
        void testDisplayUsernameVsUsername() {
            // Arrange
            Account account = Account.builder()
                    .username("Display Name")
                    .email("email@example.com")
                    .password("hash")
                    .role(Role.ROLE_USER)
                    .build();

            // Act & Assert
            assertThat(account.getDisplayUsername())
                    .isNotEqualTo(account.getUsername())
                    .isEqualTo("Display Name");
            assertThat(account.getUsername())
                    .isEqualTo("email@example.com");
        }
    }

    @Nested
    @DisplayName("Account Equality and State")
    class EqualityAndStateTests {

        @Test
        @DisplayName("two accounts with same data should be distinct objects")
        void testAccountIdentity() {
            // Arrange
            Account account1 = Account.builder()
                    .id(1L)
                    .username("Jean")
                    .email("jean@example.com")
                    .password("hash")
                    .role(Role.ROLE_USER)
                    .build();

            Account account2 = Account.builder()
                    .id(1L)
                    .username("Jean")
                    .email("jean@example.com")
                    .password("hash")
                    .role(Role.ROLE_USER)
                    .build();

            // Assert : they are different object references but may have equal content
            assertThat(account1).isNotSameAs(account2);
        }

        @Test
        @DisplayName("should handle null values gracefully")
        void testNullValues() {
            // Arrange & Act
            Account account = Account.builder()
                    .id(null)
                    .username(null)
                    .email(null)
                    .password(null)
                    .role(null)
                    .build();

            // Assert
            assertThat(account.getId()).isNull();
            assertThat(account.getDisplayUsername()).isNull();
            assertThat(account.getUsername()).isNull();
            assertThat(account.getPassword()).isNull();
            assertThat(account.getRole()).isNull();
        }
    }
}
