package fr.info803.trading_assistant.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev") // Use dev profile which provides cors + H2 config
class AppMetricsConfigIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void prometheusEndpoint_shouldExposeCustomApplicationMetrics() throws Exception {
        // Act: Request the prometheus actuator endpoint
        String body = mockMvc.perform(get("/actuator/prometheus"))
                // Assert: Endpoint should be accessible (200 OK) without any Authentication
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).isNotNull();
        
        // Assert: The custom app metrics are exposed in prometheus format
        // Note: prometheus replaces dots (.) with underscores (_)
        assertThat(body).contains("trading_assistant_users_count");
        assertThat(body).contains("trading_assistant_alerts_count");
        assertThat(body).contains("trading_assistant_favorites_count");
        assertThat(body).contains("trading_assistant_triggered_alerts_count");
    }
}
