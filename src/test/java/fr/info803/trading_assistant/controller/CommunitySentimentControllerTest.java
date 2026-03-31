package fr.info803.trading_assistant.controller;

import fr.info803.trading_assistant.dto.SentimentPollResponse;
import fr.info803.trading_assistant.dto.SentimentRequest;
import fr.info803.trading_assistant.dto.SentimentResponse;
import fr.info803.trading_assistant.entity.Account;
import fr.info803.trading_assistant.entity.SentimentType;
import fr.info803.trading_assistant.exception.AssetNotFoundException;
import fr.info803.trading_assistant.exception.GlobalExceptionHandler;
import fr.info803.trading_assistant.service.CommunitySentimentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CommunitySentimentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CommunitySentimentService sentimentService;

    @InjectMocks
    private CommunitySentimentController controller;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnPollResults() throws Exception {
        SentimentPollResponse response = new SentimentPollResponse("BTC", 10, 5);
        when(sentimentService.getPollResults("BTC")).thenReturn(response);

        mockMvc.perform(get("/assets/BTC/sentiments/poll"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BTC"))
                .andExpect(jsonPath("$.bullishCount").value(10))
                .andExpect(jsonPath("$.bearishCount").value(5))
                .andExpect(jsonPath("$.totalVotes").value(15))
                .andExpect(jsonPath("$.bullishPercentage").value(66.66666666666666));
    }

    @Test
    void shouldReturn404WhenAssetNotFoundForPoll() throws Exception {
        when(sentimentService.getPollResults("UNKNOWN")).thenThrow(new AssetNotFoundException("UNKNOWN"));

        mockMvc.perform(get("/assets/UNKNOWN/sentiments/poll"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Asset not found"))
                .andExpect(jsonPath("$.symbol").value("UNKNOWN"));
    }

    @Test
    void shouldPutSentiment() throws Exception {
        SentimentRequest request = new SentimentRequest(SentimentType.BULLISH);
        SentimentResponse response = new SentimentResponse("BTC", SentimentType.BULLISH, LocalDateTime.now());
        
        when(sentimentService.putSentiment(any(), eq("BTC"), any(SentimentRequest.class))).thenReturn(response);

        mockMvc.perform(put("/assets/BTC/sentiments/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BTC"))
                .andExpect(jsonPath("$.type").value("BULLISH"))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void shouldReturnBadRequestWhenPutSentimentWithInvalidPayload() throws Exception {
        String invalidPayload = "{\"type\": null}";

        mockMvc.perform(put("/assets/BTC/sentiments/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldGetMySentiment() throws Exception {
        SentimentResponse response = new SentimentResponse("BTC", SentimentType.BULLISH, LocalDateTime.now());
        when(sentimentService.getUserSentiment(any(), eq("BTC"))).thenReturn(response);

        mockMvc.perform(get("/assets/BTC/sentiments/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BTC"))
                .andExpect(jsonPath("$.type").value("BULLISH"))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void shouldReturnNoContentWhenNoSentiment() throws Exception {
        when(sentimentService.getUserSentiment(any(), eq("BTC"))).thenReturn(null);

        mockMvc.perform(get("/assets/BTC/sentiments/me"))
                .andExpect(status().isNoContent());
    }
}
