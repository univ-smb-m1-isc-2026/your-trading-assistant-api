package fr.info803.trading_assistant.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import fr.info803.trading_assistant.dto.ProfileResponse;
import fr.info803.trading_assistant.dto.UpdateProfileRequest;
import fr.info803.trading_assistant.dto.UpdateProfileResponse;
import fr.info803.trading_assistant.entity.Account;
import fr.info803.trading_assistant.entity.Role;
import fr.info803.trading_assistant.exception.GlobalExceptionHandler;
import fr.info803.trading_assistant.service.AccountService;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileController Unit Tests")
class ProfileControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AccountService accountService;

    @InjectMocks
    private ProfileController profileController;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Account mockAccount;

    @BeforeEach
    void setUp() {
        // standaloneSetup avoids full Spring context initialization
        mockMvc = MockMvcBuilders.standaloneSetup(profileController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new org.springframework.web.method.support.HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
                        return parameter.getParameterType().equals(Account.class);
                    }

                    @Override
                    public Object resolveArgument(org.springframework.core.MethodParameter parameter,
                                                  org.springframework.web.method.support.ModelAndViewContainer mavContainer,
                                                  org.springframework.web.context.request.NativeWebRequest webRequest,
                                                  org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                        return mockAccount; // Inject our mock user for @AuthenticationPrincipal
                    }
                })
                .build();

        mockAccount = Account.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .role(Role.ROLE_USER)
                .discordWebhook("http://discord.webhook")
                .build();
    }

    @Nested
    @DisplayName("GET /profile")
    class GetProfileTests {

        @Test
        @DisplayName("should return 200 and the profile of the current user")
        void shouldReturnProfile() throws Exception {
            ProfileResponse response = ProfileResponse.builder()
                    .username("testuser")
                    .email("test@example.com")
                    .role(Role.ROLE_USER)
                    .discordWebhook("http://discord.webhook")
                    .build();

            when(accountService.getProfile(mockAccount)).thenReturn(response);

            mockMvc.perform(get("/profile"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("testuser"))
                    .andExpect(jsonPath("$.email").value("test@example.com"))
                    .andExpect(jsonPath("$.role").value("ROLE_USER"))
                    .andExpect(jsonPath("$.discordWebhook").value("http://discord.webhook"));
        }
    }

    @Nested
    @DisplayName("PUT /profile")
    class UpdateProfileTests {

        @Test
        @DisplayName("should update profile and return updated data with token")
        void shouldUpdateProfile() throws Exception {
            UpdateProfileRequest request = UpdateProfileRequest.builder()
                    .username("newUsername")
                    .email("new@example.com")
                    .build();

            ProfileResponse profileResponse = ProfileResponse.builder()
                    .username("newUsername")
                    .email("new@example.com")
                    .role(Role.ROLE_USER)
                    .build();

            UpdateProfileResponse updateResponse = UpdateProfileResponse.builder()
                    .profile(profileResponse)
                    .token("new-jwt-token")
                    .build();

            when(accountService.updateProfile(eq(mockAccount), any(UpdateProfileRequest.class)))
                    .thenReturn(updateResponse);

            mockMvc.perform(put("/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("new-jwt-token"))
                    .andExpect(jsonPath("$.profile.username").value("newUsername"));
        }

        @Test
        @DisplayName("should fail with 400 Bad Request if email is invalid")
        void shouldFailIfInvalidEmail() throws Exception {
            UpdateProfileRequest request = UpdateProfileRequest.builder()
                    .email("invalid-email")
                    .build();

            mockMvc.perform(put("/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should update password when oldPassword and newPassword are provided")
        void shouldUpdatePassword() throws Exception {
            UpdateProfileRequest request = UpdateProfileRequest.builder()
                    .oldPassword("old-pass")
                    .newPassword("new-pass")
                    .build();

            ProfileResponse profileResponse = ProfileResponse.builder()
                    .username("testuser")
                    .email("test@example.com")
                    .role(Role.ROLE_USER)
                    .build();

            UpdateProfileResponse updateResponse = UpdateProfileResponse.builder()
                    .profile(profileResponse)
                    .token("new-jwt-token")
                    .build();

            when(accountService.updateProfile(eq(mockAccount), any(UpdateProfileRequest.class)))
                    .thenReturn(updateResponse);

            mockMvc.perform(put("/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("new-jwt-token"))
                    .andExpect(jsonPath("$.profile.username").value("testuser"));
        }
    }

    @Nested
    @DisplayName("DELETE /profile")
    class DeleteProfileTests {

        @Test
        @DisplayName("should delete profile and return 204 No Content")
        void shouldDeleteProfile() throws Exception {
            mockMvc.perform(delete("/profile"))
                    .andExpect(status().isNoContent());

            verify(accountService).deleteProfile(mockAccount);
        }
    }
}
