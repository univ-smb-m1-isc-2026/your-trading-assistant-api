package fr.info803.trading_assistant.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import fr.info803.trading_assistant.dto.AuthResponse;
import fr.info803.trading_assistant.dto.LoginRequest;
import fr.info803.trading_assistant.dto.RegisterRequest;
import fr.info803.trading_assistant.entity.Account;
import fr.info803.trading_assistant.entity.Role;
import fr.info803.trading_assistant.repository.AccountRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the AccountService class.
 *
 * Tests the business logic for user registration and login in isolation (no Spring context).
 * All dependencies are mocked using Mockito.
 *
 * Covers:
 * - Registration: email validation, password encoding, role assignment, JWT generation
 * - Login: authentication delegation, account retrieval, JWT generation
 * - Error handling: duplicate email, invalid credentials, missing account
 */
@DisplayName("AccountService Unit Tests")
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AccountService accountService;

    @Nested
    @DisplayName("Register Method")
    class RegisterTests {

        @Test
        @DisplayName("should register new account successfully")
        void shouldRegisterNewAccountSuccessfully() {
            // Arrange
            String plainPassword = "password123";
            String encodedPassword = "hashed_password_bcrypt";
            String expectedToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqZWFuQGV4YW1wbGUuY29tIiwiaWF0IjoxNjU0OTAyNjE0LCJleHAiOjE2NTQ5ODkwMTR9";

            RegisterRequest request = new RegisterRequest();
            request.setUsername("Jean Dupont");
            request.setEmail("jean@example.com");
            request.setPassword(plainPassword);

            when(accountRepository.existsByEmail("jean@example.com")).thenReturn(false);
            when(passwordEncoder.encode(plainPassword)).thenReturn(encodedPassword);
            when(jwtService.generateToken(any(Account.class))).thenReturn(expectedToken);

            // Act
            AuthResponse response = accountService.register(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getToken()).isEqualTo(expectedToken);

            // Verify the Account was saved with correct state
            ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
            verify(accountRepository).save(accountCaptor.capture());

            Account savedAccount = accountCaptor.getValue();
            assertThat(savedAccount)
                    .satisfies(a -> {
                        assertThat(a.getEmail()).isEqualTo("jean@example.com");
                        assertThat(a.getDisplayUsername()).isEqualTo("Jean Dupont");
                        assertThat(a.getPassword()).isEqualTo(encodedPassword);
                        assertThat(a.getRole()).isEqualTo(Role.ROLE_USER);
                    });

            // Verify JWT was generated with the saved account
            verify(jwtService).generateToken(savedAccount);
        }

        @Test
        @DisplayName("should encode password before saving account")
        void shouldEncodePasswordBeforeSaving() {
            // Arrange
            String plainPassword = "plainTextPassword";
            String encodedPassword = "encodedHash";

            RegisterRequest request = new RegisterRequest();
            request.setUsername("User");
            request.setEmail("user@example.com");
            request.setPassword(plainPassword);

            when(accountRepository.existsByEmail("user@example.com")).thenReturn(false);
            when(passwordEncoder.encode(plainPassword)).thenReturn(encodedPassword);
            when(jwtService.generateToken(any(Account.class))).thenReturn("token");

            // Act
            accountService.register(request);

            // Assert
            // Verify password encoder was called with plaintext password
            verify(passwordEncoder).encode(plainPassword);

            // Verify the saved account contains encoded password, not plaintext
            ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
            verify(accountRepository).save(accountCaptor.capture());

            assertThat(accountCaptor.getValue().getPassword())
                    .isEqualTo(encodedPassword)
                    .isNotEqualTo(plainPassword);
        }

        @Test
        @DisplayName("should assign ROLE_USER by default to new account")
        void shouldAssignRoleUserByDefault() {
            // Arrange
            RegisterRequest request = new RegisterRequest();
            request.setUsername("NewUser");
            request.setEmail("newuser@example.com");
            request.setPassword("password");

            when(accountRepository.existsByEmail("newuser@example.com")).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hash");
            when(jwtService.generateToken(any(Account.class))).thenReturn("token");

            // Act
            accountService.register(request);

            // Assert
            ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
            verify(accountRepository).save(accountCaptor.capture());

            assertThat(accountCaptor.getValue().getRole()).isEqualTo(Role.ROLE_USER);
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when email already exists")
        void shouldThrowIllegalArgumentExceptionWhenEmailAlreadyExists() {
            // Arrange
            RegisterRequest request = new RegisterRequest();
            request.setUsername("Existing User");
            request.setEmail("existing@example.com");
            request.setPassword("password");

            when(accountRepository.existsByEmail("existing@example.com")).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> accountService.register(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Un compte existe déjà avec cet email");

            // Verify account was NOT saved
            verify(accountRepository, never()).save(any());
            verify(jwtService, never()).generateToken(any());
        }
    }

    @Nested
    @DisplayName("Login Method")
    class LoginTests {

        @Test
        @DisplayName("should login successfully with valid credentials")
        void shouldLoginSuccessfullyWithValidCredentials() {
            // Arrange
            LoginRequest request = new LoginRequest();
            request.setEmail("jean@example.com");
            request.setPassword("password123");

            Account account = Account.builder()
                    .id(1L)
                    .email("jean@example.com")
                    .username("Jean Dupont")
                    .password("encodedPassword")
                    .role(Role.ROLE_USER)
                    .build();

            String expectedToken = "eyJhbGciOiJIUzI1NiJ9.token";

            // AuthenticationManager succeeds (no exception thrown)
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(null);
            when(accountRepository.findByEmail("jean@example.com")).thenReturn(Optional.of(account));
            when(jwtService.generateToken(account)).thenReturn(expectedToken);

            // Act
            AuthResponse response = accountService.login(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getToken()).isEqualTo(expectedToken);

            // Verify authentication was delegated
            ArgumentCaptor<UsernamePasswordAuthenticationToken> tokenCaptor =
                    ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
            verify(authenticationManager).authenticate(tokenCaptor.capture());

            UsernamePasswordAuthenticationToken authToken = tokenCaptor.getValue();
            assertThat(authToken.getPrincipal()).isEqualTo("jean@example.com");
            assertThat(authToken.getCredentials()).isEqualTo("password123");

            // Verify JWT was generated with the retrieved account
            verify(jwtService).generateToken(account);
        }

        @Test
        @DisplayName("should delegate authentication to AuthenticationManager")
        void shouldDelegateAuthenticationToAuthenticationManager() {
            // Arrange
            LoginRequest request = new LoginRequest();
            request.setEmail("user@example.com");
            request.setPassword("password");

            Account account = Account.builder()
                    .id(2L)
                    .email("user@example.com")
                    .username("User")
                    .password("hash")
                    .role(Role.ROLE_USER)
                    .build();

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(null);
            when(accountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(account));
            when(jwtService.generateToken(any(Account.class))).thenReturn("token");

            // Act
            accountService.login(request);

            // Assert
            // Verify that authenticate() was called with correct email and password
            verify(authenticationManager).authenticate(
                    new UsernamePasswordAuthenticationToken("user@example.com", "password")
            );
        }

        @Test
        @DisplayName("should throw BadCredentialsException when credentials are invalid")
        void shouldThrowBadCredentialsExceptionWhenCredentialsAreInvalid() {
            // Arrange
            LoginRequest request = new LoginRequest();
            request.setEmail("jean@example.com");
            request.setPassword("wrongPassword");

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            // Act & Assert
            assertThatThrownBy(() -> accountService.login(request))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Bad credentials");

            // Verify account was NOT retrieved or JWT generated
            verify(accountRepository, never()).findByEmail(anyString());
            verify(jwtService, never()).generateToken(any());
        }

        @Test
        @DisplayName("should throw UsernameNotFoundException when account not found after auth")
        void shouldThrowUsernameNotFoundExceptionWhenAccountMissing() {
            // Arrange
            LoginRequest request = new LoginRequest();
            request.setEmail("phantom@example.com");
            request.setPassword("password");

            // AuthenticationManager succeeds, but account doesn't exist (edge case)
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(null);
            when(accountRepository.findByEmail("phantom@example.com")).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> accountService.login(request))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessage("Compte introuvable");

            // Verify JWT was NOT generated
            verify(jwtService, never()).generateToken(any());
        }
    }
}
