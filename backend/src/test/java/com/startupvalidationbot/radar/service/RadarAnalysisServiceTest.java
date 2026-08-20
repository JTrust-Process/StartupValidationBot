package com.startupvalidationbot.radar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.startupvalidationbot.radar.RadarDomain.Analysis;
import com.startupvalidationbot.radar.RadarDomain.Company;
import com.startupvalidationbot.radar.RadarStore;
import com.startupvalidationbot.radar.RadarStore.AnalysisPayload;
import com.startupvalidationbot.radar.ai.PublicCompanyAnalysisInput;
import com.startupvalidationbot.radar.ai.PublicRadarAnalysisInputFactory;
import com.startupvalidationbot.radar.ai.RadarAiException;
import com.startupvalidationbot.radar.ai.RadarAiOutput;
import com.startupvalidationbot.radar.ai.RadarAiProvider;
import com.startupvalidationbot.radar.ai.RadarAiResponse;

@ExtendWith(MockitoExtension.class)
class RadarAnalysisServiceTest {
    @Mock
    private RadarStore store;
    @Mock
    private PublicRadarAnalysisInputFactory inputFactory;
    @Mock
    private RadarAiProvider provider;

    private RadarScoringService scoringService;
    private Company company;
    private PublicCompanyAnalysisInput input;

    @BeforeEach
    void setUp() {
        scoringService = new RadarScoringService("artificial intelligence,developer tools,automation");
        company = company("Public startup description");
        input = input("Public startup description");
        when(inputFactory.create(company)).thenReturn(input);
    }

    @Test
    void aiDisabledUsesDeterministicAnalysisWithoutProviderRequest() {
        when(store.findCachedAnalysis(anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString())).thenReturn(Optional.empty());

        service(false, 25).analyze(company, "RADAR");

        verifyNoInteractions(provider);
        verify(store).saveAnalysis(eq(company.id()), eq("RADAR"), anyString(), eq("prompt-v2"), eq("schema-v1"),
                eq("DETERMINISTIC"), eq("deterministic"), eq("deterministic-radar-v1"), any());
    }

    @Test
    void validAiOutputIsPersistedWithDeterministicFinalScores() {
        configureProvider();
        when(store.findCachedAnalysis(anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString())).thenReturn(Optional.empty());
        when(provider.analyzeCompany(input)).thenReturn(response("openai/gpt-oss-20b"));

        service(true, 25).analyze(company, "RADAR");

        ArgumentCaptor<AnalysisPayload> payload = ArgumentCaptor.forClass(AnalysisPayload.class);
        verify(store).saveAnalysis(eq(company.id()), eq("RADAR"), anyString(), eq("prompt-v2"), eq("schema-v1"),
                eq("HYBRID"), eq("groq"), eq("openai/gpt-oss-20b"), payload.capture());
        assertThat(payload.getValue().summary()).isEqualTo("Structured public summary");
        assertThat(payload.getValue().sector()).isEqualTo("Enterprise Software");
        assertThat(payload.getValue().radarScore()).isEqualTo(scoringService.score(company).radarScore());
        verify(store).recordAiAttempt(eq(company.id()), eq("RADAR"), eq("groq"),
                eq("openai/gpt-oss-20b"), anyString(), eq("prompt-v2"), eq("schema-v1"), eq("SUCCESS"),
                eq(null), eq(null), eq(0), eq(120L), eq(100L), eq(40L));
    }

    @Test
    void sparseAiOutputCannotEraseMeaningfulDeterministicFieldsOrLists() {
        configureProvider();
        when(store.findCachedAnalysis(anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString())).thenReturn(Optional.empty());
        when(provider.analyzeCompany(input)).thenReturn(new RadarAiResponse(sparseOutput(),
                "openai/gpt-oss-20b", 0, 50, 20L, 10L));

        service(true, 25).analyze(company, "RADAR");

        ArgumentCaptor<AnalysisPayload> payload = ArgumentCaptor.forClass(AnalysisPayload.class);
        verify(store).saveAnalysis(eq(company.id()), eq("RADAR"), anyString(), eq("prompt-v2"), eq("schema-v1"),
                eq("HYBRID"), eq("groq"), eq("openai/gpt-oss-20b"), payload.capture());
        assertThat(payload.getValue().summary()).isEqualTo("Public startup description");
        assertThat(payload.getValue().sector()).isEqualTo("Software");
        assertThat(payload.getValue().businessModel()).isEqualTo("Unknown from current sources");
        assertThat(payload.getValue().trendTags()).containsExactly("Developer Tools");
        assertThat(payload.getValue().monitoringTriggers()).isNotEmpty();
        assertThat(payload.getValue().risks()).isNotEmpty();
    }

    @Test
    void meaningfulAiListsAreAddedWithoutRemovingExistingEvidence() {
        configureProvider();
        when(store.findCachedAnalysis(anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString())).thenReturn(Optional.empty());
        when(provider.analyzeCompany(input)).thenReturn(response("openai/gpt-oss-20b"));

        service(true, 25).analyze(company, "RADAR");

        ArgumentCaptor<AnalysisPayload> payload = ArgumentCaptor.forClass(AnalysisPayload.class);
        verify(store).saveAnalysis(eq(company.id()), eq("RADAR"), anyString(), eq("prompt-v2"), eq("schema-v1"),
                eq("HYBRID"), eq("groq"), eq("openai/gpt-oss-20b"), payload.capture());
        assertThat(payload.getValue().trendTags()).contains("Developer Tools");
        assertThat(payload.getValue().monitoringTriggers())
                .contains("New independent source", "Verified customer update");
        assertThat(payload.getValue().risks())
                .contains("Only one discovery source is currently captured.", "Early-stage evidence");
    }

    @Test
    void cacheHitReusesAnalysisWithoutProviderRequest() {
        configureProvider();
        Analysis cached = org.mockito.Mockito.mock(Analysis.class);
        when(store.findCachedAnalysis(anyLong(), eq("RADAR"), anyString(), eq("prompt-v2"), eq("schema-v1"),
                eq("groq"), eq("openai/gpt-oss-20b"))).thenReturn(Optional.of(cached));

        Analysis result = service(true, 25).analyze(company, "RADAR");

        assertThat(result).isSameAs(cached);
        verify(provider, never()).analyzeCompany(any());
    }

    @Test
    void changedPublicInputRequestsNewAnalysis() {
        configureProvider();
        PublicCompanyAnalysisInput changed = input("Changed public description");
        when(inputFactory.create(company)).thenReturn(input, changed);
        when(store.findCachedAnalysis(anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString())).thenReturn(Optional.empty());
        when(provider.analyzeCompany(any())).thenReturn(response("openai/gpt-oss-20b"));
        RadarAnalysisService service = service(true, 25);

        service.analyze(company, "RADAR");
        service.analyze(company, "RADAR");

        verify(provider).analyzeCompany(input);
        verify(provider).analyzeCompany(changed);
    }

    @ParameterizedTest
    @MethodSource("providerFailures")
    void providerFailureIsRecordedAndFallsBackWithoutFailingCompany(String errorType, String message,
            int attempts, Integer httpStatus, String providerErrorType, String providerErrorCode) {
        configureProvider();
        when(store.findCachedAnalysis(anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString())).thenReturn(Optional.empty());
        when(provider.analyzeCompany(input)).thenThrow(new RadarAiException(errorType, message, attempts > 1,
                attempts, httpStatus, providerErrorType, providerErrorCode));

        service(true, 25).analyze(company, "RADAR");

        verify(store).recordAiAttempt(eq(company.id()), eq("RADAR"), eq("groq"),
                eq("openai/gpt-oss-20b"), anyString(), eq("prompt-v2"), eq("schema-v1"), eq("FAILED"),
                eq(errorType), eq(message), eq(Math.max(0, attempts - 1)), eq(null), eq(null), eq(null),
                eq(httpStatus), eq(providerErrorType), eq(providerErrorCode));
        verify(store).saveAnalysis(eq(company.id()), eq("RADAR"), anyString(), eq("prompt-v2"), eq("schema-v1"),
                eq("DETERMINISTIC"), eq("deterministic"), eq("deterministic-radar-v1"), any());
    }

    private static Stream<Arguments> providerFailures() {
        return Stream.of(
                Arguments.of("PROVIDER_REQUEST_REJECTED", "Groq rejected the request", 1, 400,
                        "invalid_request_error", "unsupported_parameter"),
                Arguments.of("RATE_LIMITED", "Groq returned HTTP 429", 3, 429, null, null),
                Arguments.of("PROVIDER_UNAVAILABLE", "Groq returned HTTP 503", 2, 503,
                        "server_error", null),
                Arguments.of("TIMEOUT", "Groq request timed out", 2, null, null, null),
                Arguments.of("MALFORMED_RESPONSE", "Groq returned malformed JSON", 2, 200, null, null),
                Arguments.of("STRUCTURED_OUTPUT_REJECTED", "Generated JSON did not match the schema", 2, 400,
                        "invalid_request_error", "json_validate_failed"));
    }

    @Test
    void exhaustedBudgetStopsAdditionalProviderCalls() {
        configureProvider();
        when(store.findCachedAnalysis(anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString())).thenReturn(Optional.empty());
        when(provider.analyzeCompany(any())).thenReturn(response("openai/gpt-oss-20b"));
        RadarAnalysisService service = service(true, 1);
        var budget = service.newRunBudget();

        service.analyze(company, "RADAR", budget);
        service.analyze(company, "RADAR", budget);

        verify(provider).analyzeCompany(input);
        verify(store).recordAiAttempt(eq(company.id()), eq("RADAR"), eq("groq"),
                eq("openai/gpt-oss-20b"), anyString(), eq("prompt-v2"), eq("schema-v1"), eq("SKIPPED"),
                eq("BUDGET_EXHAUSTED"), anyString(), eq(0), eq(null), eq(null), eq(null));
    }

    @Test
    void deepDiveUsesConfiguredLargeModel() {
        configureProvider();
        when(store.findCachedAnalysis(anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString())).thenReturn(Optional.empty());
        when(provider.generateDeepDive(input)).thenReturn(response("openai/gpt-oss-120b"));

        service(true, 25).analyze(company, "DEEP_DIVE");

        verify(provider).generateDeepDive(input);
        verify(provider, never()).analyzeCompany(any());
        verify(store).saveAnalysis(eq(company.id()), eq("DEEP_DIVE"), anyString(), eq("prompt-v2"),
                eq("schema-v1"), eq("HYBRID"), eq("groq"), eq("openai/gpt-oss-120b"), any());
    }

    private RadarAnalysisService service(boolean enabled, int maxItems) {
        return new RadarAnalysisService(store, scoringService, inputFactory, List.of(provider), enabled, "groq",
                "prompt-v2", "schema-v1", maxItems);
    }

    private void configureProvider() {
        lenient().when(provider.providerId()).thenReturn("groq");
        lenient().when(provider.isConfigured()).thenReturn(true);
        lenient().when(provider.routineModel()).thenReturn("openai/gpt-oss-20b");
        lenient().when(provider.deepDiveModel()).thenReturn("openai/gpt-oss-120b");
    }

    private static RadarAiResponse response(String model) {
        return new RadarAiResponse(output(), model, 0, 120, 100L, 40L);
    }

    private static RadarAiOutput output() {
        return new RadarAiOutput("Structured public summary", "Enterprise Software", "A costly workflow", "Automation software",
                "Subscription", List.of("Developer Tools"), "Seed", List.of(), "Unknown", List.of(),
                List.of("Public launch"), List.of("Technical workflow"), List.of("Growing category"),
                List.of("Matches developer tools"), List.of("Early-stage evidence"),
                List.of("Could automate a recurring workflow"), List.of("May lack distribution"),
                "It tests a useful automation wedge.", "Matches configured developer-tool interests.",
                List.of("Verified customer update"), List.of("Source-backed launch"),
                List.of("Developer tools match"), "Unknown", "Potential software research overlap",
                List.of("What traction is verified?"), "MEDIUM", List.of("Public source describes the product."),
                List.of("Market fit remains an inference."), List.of("https://example.com/source"));
    }

    private static RadarAiOutput sparseOutput() {
        return new RadarAiOutput("Unknown", "N/A", "Unknown", "Unknown", "Unknown", List.of(), "Unknown",
                List.of(), "Unknown", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), "Unknown", "Unknown", List.of(), List.of(), List.of(), "Unknown", "Unknown", List.of(),
                "LOW", List.of(), List.of(), List.of());
    }

    private static Company company(String description) {
        return new Company(7L, "Example Systems", "example.com", "https://example.com", description, "Software",
                List.of("Developer Tools"), "New York", 2025, List.of(), 0, 0, "", 1,
                LocalDateTime.now(), LocalDateTime.now(), false, false);
    }

    private static PublicCompanyAnalysisInput input(String description) {
        return new PublicCompanyAnalysisInput(7L, "Example Systems", "example.com", "https://example.com",
                description, "Software", List.of("Developer Tools"), "New York", 2025, "Unknown", List.of(),
                List.of(), List.of(), List.of(),
                List.of(new PublicCompanyAnalysisInput.PublicSourceEvidence("RSS", "Public source",
                        "https://example.com/source", description)), 1);
    }
}
