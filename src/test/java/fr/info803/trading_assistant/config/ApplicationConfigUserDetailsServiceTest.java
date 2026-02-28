package fr.info803.trading_assistant.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import fr.info803.trading_assistant.entity.Account;
import fr.info803.trading_assistant.entity.Role;
import fr.info803.trading_assistant.repository.AccountRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the UserDetailsService bean defined in ApplicationConfig.
 *
 * Tests the UserDetailsService bean in isolation (no Spring context).
 * The bean is a lambda function that:
 *   1. Takes an email (username in Spring Security terminology)
 *   2. Loads the Account from the database via AccountRepository
 *   3. Returns the Account if found
 *   4. Throws UsernameNotFoundException if not found
 *
 * Covers:
 * - Account loaded successfully by email
 * - UsernameNotFoundException thrown when account not found
 * - Exception message contains the email for debugging
 */
@DisplayName("ApplicationConfig - UserDetailsService Bean Unit Tests")
@ExtendWith(MockitoExtension.class)
class ApplicationConfigUserDetailsServiceTest {

    @Mock
    private AccountRepository accountRepository;

    private ApplicationConfig applicationConfig;
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        // Create ApplicationConfig instance with mocked repository
        applicationConfig = new ApplicationConfig(accountRepository);

        // Get the UserDetailsService bean from the configuration
        userDetailsService = applicationConfig.userDetailsService();
    }

    @Nested
    @DisplayName("loadUserByUsername()")
    class LoadUserByUsernameTests {

        @Test
        @DisplayName("should load account successfully when email exists")
        void shouldLoadAccountSuccessfully() {
            // Arrange
            String email = "jean@example.com";
            Account account = Account.builder()
                    .id(1L)
                    .username("Jean Dupont")
                    .email(email)
                    .password("hashedPassword")
                    .role(Role.ROLE_USER)
                    .build();

            when(accountRepository.findByEmail(email)).thenReturn(Optional.of(account));

            // Act
            Account loadedAccount = (Account) userDetailsService.loadUserByUsername(email);

            // Assert
            assertThat(loadedAccount).isNotNull();
            assertThat(loadedAccount.getId()).isEqualTo(1L);
            assertThat(loadedAccount.getEmail()).isEqualTo(email);
            assertThat(loadedAccount.getDisplayUsername()).isEqualTo("Jean Dupont");
            assertThat(loadedAccount.getPassword()).isEqualTo("hashedPassword");
            assertThat(loadedAccount.getRole()).isEqualTo(Role.ROLE_USER);

            // Verify repository was called with correct email
            verify(accountRepository).findByEmail(email);
        }

        @Test
        @DisplayName("should throw UsernameNotFoundException when account not found")
        void shouldThrowUsernameNotFoundExceptionWhenAccountNotFound() {
            // Arrange
            String email = "nonexistent@example.com";

            when(accountRepository.findByEmail(email)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> userDetailsService.loadUserByUsername(email))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessage("Compte introuvable : " + email);

            // Verify repository was called
            verify(accountRepository).findByEmail(email);
        }

        @Test
        @DisplayName("should include email in exception message for debugging")
        void shouldIncludeEmailInExceptionMessage() {
            // Arrange
            String email = "debug@example.com";

            when(accountRepository.findByEmail(email)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> userDetailsService.loadUserByUsername(email))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessageContaining(email)
                    .hasMessageContaining("Compte introuvable");
        }
    }

    @Nested
    @DisplayName("UserDetails Contract Verification")
    class UserDetailsContractTests {

        @Test
        @DisplayName("should return Account implementing UserDetails interface")
        void shouldReturnAccountImplementingUserDetails() {
            // Arrange
            String email = "user@example.com";
            Account account = Account.builder()
                    .id(2L)
                    .username("User")
                    .email(email)
                    .password("hash")
                    .role(Role.ROLE_ADMIN)
                    .build();

            when(accountRepository.findByEmail(email)).thenReturn(Optional.of(account));

            // Act
            var loadedUser = userDetailsService.loadUserByUsername(email);

            // Assert - Verify it implements UserDetails contract
            assertThat(loadedUser).isInstanceOf(Account.class);
            assertThat(loadedUser.getUsername()).isEqualTo(email); // UserDetails.getUsername() returns email
            assertThat(loadedUser.getPassword()).isEqualTo("hash"); // UserDetails.getPassword()
            assertThat(loadedUser.getAuthorities()).isNotNull(); // UserDetails.getAuthorities()
            assertThat(loadedUser.isAccountNonExpired()).isTrue(); // UserDetails.isAccountNonExpired()
            assertThat(loadedUser.isAccountNonLocked()).isTrue(); // UserDetails.isAccountNonLocked()
            assertThat(loadedUser.isCredentialsNonExpired()).isTrue(); // UserDetails.isCredentialsNonExpired()
            assertThat(loadedUser.isEnabled()).isTrue(); // UserDetails.isEnabled()
        }

        @Test
        @DisplayName("should return account with ROLE_ADMIN when set")
        void shouldReturnAccountWithRoleAdmin() {
            // Arrange
            String email = "admin@example.com";
            Account adminAccount = Account.builder()
                    .id(3L)
                    .username("Admin User")
                    .email(email)
                    .password("adminHash")
                    .role(Role.ROLE_ADMIN)
                    .build();

            when(accountRepository.findByEmail(email)).thenReturn(Optional.of(adminAccount));

            // Act
            Account loadedAccount = (Account) userDetailsService.loadUserByUsername(email);

            // Assert
            assertThat(loadedAccount.getRole()).isEqualTo(Role.ROLE_ADMIN);
            assertThat(loadedAccount.getAuthorities()).isNotEmpty();
        }
    }
}
