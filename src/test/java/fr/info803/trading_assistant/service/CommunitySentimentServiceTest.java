package fr.info803.trading_assistant.service;

import fr.info803.trading_assistant.dto.SentimentPollResponse;
import fr.info803.trading_assistant.dto.SentimentRequest;
import fr.info803.trading_assistant.dto.SentimentResponse;
import fr.info803.trading_assistant.entity.Account;
import fr.info803.trading_assistant.entity.Asset;
import fr.info803.trading_assistant.entity.CommunitySentiment;
import fr.info803.trading_assistant.entity.SentimentType;
import fr.info803.trading_assistant.exception.AssetNotFoundException;
import fr.info803.trading_assistant.repository.AssetRepository;
import fr.info803.trading_assistant.repository.CommunitySentimentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunitySentimentServiceTest {

    @Mock
    private CommunitySentimentRepository sentimentRepository;

    @Mock
    private AssetRepository assetRepository;

    @InjectMocks
    private CommunitySentimentService sentimentService;

    @Captor
    private ArgumentCaptor<CommunitySentiment> sentimentCaptor;

    private Account testAccount;
    private Asset testAsset;

    @BeforeEach
    void setUp() {
        testAccount = Account.builder()
                .id(1L)
                .email("test@example.com")
                .build();

        testAsset = Asset.builder()
                .id(100L)
                .symbol("BTC")
                .build();
    }

    @Nested
    class PutSentimentTests {

        @Test
        void shouldCreateNewSentimentWhenNoneExists() {
            SentimentRequest request = new SentimentRequest(SentimentType.BULLISH);
            
            when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(testAsset));
            when(sentimentRepository.findByAccountAndAsset(testAccount, testAsset)).thenReturn(Optional.empty());
            
            CommunitySentiment savedSentiment = CommunitySentiment.builder()
                    .id(1L)
                    .account(testAccount)
                    .asset(testAsset)
                    .type(SentimentType.BULLISH)
                    .updatedAt(LocalDateTime.now())
                    .build();
            when(sentimentRepository.save(any(CommunitySentiment.class))).thenReturn(savedSentiment);

            SentimentResponse response = sentimentService.putSentiment(testAccount, "BTC", request);

            verify(sentimentRepository).save(sentimentCaptor.capture());
            CommunitySentiment captured = sentimentCaptor.getValue();
            
            assertThat(captured.getAccount()).isEqualTo(testAccount);
            assertThat(captured.getAsset()).isEqualTo(testAsset);
            assertThat(captured.getType()).isEqualTo(SentimentType.BULLISH);
            
            assertThat(response.symbol()).isEqualTo("BTC");
            assertThat(response.type()).isEqualTo(SentimentType.BULLISH);
            assertThat(response.updatedAt()).isNotNull();
        }

        @Test
        void shouldUpdateExistingSentiment() {
            SentimentRequest request = new SentimentRequest(SentimentType.BEARISH);
            
            CommunitySentiment existingSentiment = CommunitySentiment.builder()
                    .id(1L)
                    .account(testAccount)
                    .asset(testAsset)
                    .type(SentimentType.BULLISH)
                    .updatedAt(LocalDateTime.now().minusDays(1))
                    .build();

            when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(testAsset));
            when(sentimentRepository.findByAccountAndAsset(testAccount, testAsset)).thenReturn(Optional.of(existingSentiment));
            when(sentimentRepository.save(any(CommunitySentiment.class))).thenReturn(existingSentiment);

            SentimentResponse response = sentimentService.putSentiment(testAccount, "BTC", request);

            verify(sentimentRepository).save(sentimentCaptor.capture());
            CommunitySentiment captured = sentimentCaptor.getValue();
            
            assertThat(captured.getId()).isEqualTo(1L);
            assertThat(captured.getType()).isEqualTo(SentimentType.BEARISH);
            
            assertThat(response.type()).isEqualTo(SentimentType.BEARISH);
        }

        @Test
        void shouldThrowExceptionWhenAssetNotFound() {
            SentimentRequest request = new SentimentRequest(SentimentType.BULLISH);
            
            when(assetRepository.findBySymbol("UNKNOWN")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sentimentService.putSentiment(testAccount, "UNKNOWN", request))
                    .isInstanceOf(AssetNotFoundException.class)
                    .hasMessageContaining("UNKNOWN");
                    
            verify(sentimentRepository, never()).save(any());
        }
    }

    @Nested
    class GetPollResultsTests {

        @Test
        void shouldReturnPollResults() {
            when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(testAsset));
            
            SentimentPollResponse mockResponse = new SentimentPollResponse("BTC", 10, 5);
            when(sentimentRepository.getPollResultsBySymbol("BTC")).thenReturn(Optional.of(mockResponse));

            SentimentPollResponse result = sentimentService.getPollResults("BTC");

            assertThat(result.symbol()).isEqualTo("BTC");
            assertThat(result.bullishCount()).isEqualTo(10);
            assertThat(result.bearishCount()).isEqualTo(5);
            assertThat(result.totalVotes()).isEqualTo(15);
            assertThat(result.bullishPercentage()).isCloseTo(66.66, org.assertj.core.data.Offset.offset(0.1));
        }

        @Test
        void shouldReturnZerosWhenNoVotes() {
            when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(testAsset));
            when(sentimentRepository.getPollResultsBySymbol("BTC")).thenReturn(Optional.empty());

            SentimentPollResponse result = sentimentService.getPollResults("BTC");

            assertThat(result.symbol()).isEqualTo("BTC");
            assertThat(result.bullishCount()).isEqualTo(0);
            assertThat(result.bearishCount()).isEqualTo(0);
            assertThat(result.totalVotes()).isEqualTo(0);
            assertThat(result.bullishPercentage()).isEqualTo(0.0);
        }

        @Test
        void shouldThrowExceptionWhenAssetNotFound() {
            when(assetRepository.findBySymbol("UNKNOWN")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sentimentService.getPollResults("UNKNOWN"))
                    .isInstanceOf(AssetNotFoundException.class);
        }
    }

    @Nested
    class GetUserSentimentTests {

        @Test
        void shouldReturnUserSentimentWhenExists() {
            CommunitySentiment sentiment = CommunitySentiment.builder()
                    .account(testAccount)
                    .asset(testAsset)
                    .type(SentimentType.BULLISH)
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(testAsset));
            when(sentimentRepository.findByAccountAndAsset(testAccount, testAsset)).thenReturn(Optional.of(sentiment));

            SentimentResponse response = sentimentService.getUserSentiment(testAccount, "BTC");

            assertThat(response).isNotNull();
            assertThat(response.symbol()).isEqualTo("BTC");
            assertThat(response.type()).isEqualTo(SentimentType.BULLISH);
        }

        @Test
        void shouldReturnNullWhenNoSentimentExists() {
            when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(testAsset));
            when(sentimentRepository.findByAccountAndAsset(testAccount, testAsset)).thenReturn(Optional.empty());

            SentimentResponse response = sentimentService.getUserSentiment(testAccount, "BTC");

            assertThat(response).isNull();
        }
    }
}
