package com.startupvalidationbot.radar.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
import com.startupvalidationbot.radar.ai.RadarAiRunBudget;
import com.startupvalidationbot.radar.ai.RadarAiSchema;

@Service
public class RadarAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(RadarAnalysisService.class);
    private static final String DETERMINISTIC_PROVIDER = "deterministic";
    private static final String DETERMINISTIC_MODEL = "deterministic-radar-v1";

    private final RadarStore store;
    private final RadarScoringService scoringService;
    private final PublicRadarAnalysisInputFactory inputFactory;
    private final List<RadarAiProvider> providers;
    private final boolean aiEnabled;
    private final String configuredProvider;
    private final String promptVersion;
    private final String schemaVersion;
    private final int maxItemsPerRun;

    public RadarAnalysisService(RadarStore store, RadarScoringService scoringService,
            PublicRadarAnalysisInputFactory inputFactory, List<RadarAiProvider> providers,
            @Value("${radar.ai.enabled:false}") boolean aiEnabled,
            @Value("${radar.ai.provider:groq}") String configuredProvider,
            @Value("${radar.ai.prompt-version:radar-v2}") String promptVersion,
            @Value("${radar.ai.schema-version:" + RadarAiSchema.VERSION + "}") String schemaVersion,
            @Value("${radar.ai.max-items-per-run:25}") int maxItemsPerRun) {
        this.store = store;
        this.scoringService = scoringService;
        this.inputFactory = inputFactory;
        this.providers = List.copyOf(providers);
        this.aiEnabled = aiEnabled;
        this.configuredProvider = configuredProvider == null ? "" : configuredProvider.trim().toLowerCase(Locale.ROOT);
        this.promptVersion = promptVersion;
        this.schemaVersion = schemaVersion;
        this.maxItemsPerRun = Math.max(0, maxItemsPerRun);
    }

    public RadarAiRunBudget newRunBudget() {
        return new RadarAiRunBudget(maxItemsPerRun);
    }

    public Analysis analyze(Company company, String analysisType) {
        return analyze(company, analysisType, newRunBudget());
    }

    public Analysis analyze(Company company, String analysisType, RadarAiRunBudget budget) {
        String normalizedType = normalizeAnalysisType(analysisType);
        PublicCompanyAnalysisInput input = inputFactory.create(company);
        if (!aiEnabled) {
            return deterministic(company, normalizedType, input);
        }

        RadarAiProvider provider = providers.stream()
                .filter(candidate -> candidate.providerId().equalsIgnoreCase(configuredProvider))
                .findFirst().orElse(null);
        if (provider == null) {
            recordConfigurationFailure(company, normalizedType, input, "UNSUPPORTED_PROVIDER",
                    "Unsupported AI provider: " + configuredProvider);
            return deterministic(company, normalizedType, input);
        }

        String model = "DEEP_DIVE".equals(normalizedType) ? provider.deepDiveModel() : provider.routineModel();
        String inputHash = input.stableHash(provider.providerId(), model, promptVersion, schemaVersion);
        Analysis cached = store.findCachedAnalysis(company.id(), normalizedType, inputHash, promptVersion,
                schemaVersion, provider.providerId(), model).orElse(null);
        if (cached != null) {
            store.recordAiAttempt(company.id(), normalizedType, provider.providerId(), model, inputHash,
                    promptVersion, schemaVersion, "CACHE_HIT", null, null, 0, 0L, 0L, 0L);
            log.info("radar_ai_cache companyId={} provider={} model={} cache=hit", company.id(),
                    provider.providerId(), model);
            return cached;
        }
        if (!provider.isConfigured()) {
            recordFailure(company.id(), normalizedType, provider.providerId(), model, inputHash,
                    new RadarAiException("MISSING_CREDENTIALS", "GROQ_API_KEY is not configured", false, 0));
            return deterministic(company, normalizedType, input);
        }
        if (!budget.tryAcquire()) {
            store.recordAiAttempt(company.id(), normalizedType, provider.providerId(), model, inputHash,
                    promptVersion, schemaVersion, "SKIPPED", "BUDGET_EXHAUSTED",
                    "RADAR_AI_MAX_ITEMS_PER_RUN was reached", 0, null, null, null);
            log.info("radar_ai_call companyId={} provider={} model={} cache=miss success=false errorType=BUDGET_EXHAUSTED",
                    company.id(), provider.providerId(), model);
            return deterministic(company, normalizedType, input);
        }

        try {
            RadarAiResponse response = "DEEP_DIVE".equals(normalizedType)
                    ? provider.generateDeepDive(input)
                    : provider.analyzeCompany(input);
            AnalysisPayload payload = merge(scoringService.score(company), response.output());
            store.recordAiAttempt(company.id(), normalizedType, provider.providerId(), response.model(), inputHash,
                    promptVersion, schemaVersion, "SUCCESS", null, null, response.retryCount(),
                    response.latencyMs(), response.inputTokens(), response.outputTokens());
            return store.saveAnalysis(company.id(), normalizedType, inputHash, promptVersion, schemaVersion,
                    "HYBRID", provider.providerId(), response.model(), payload);
        } catch (RadarAiException error) {
            recordFailure(company.id(), normalizedType, provider.providerId(), model, inputHash, error);
            return deterministic(company, normalizedType, input);
        } catch (RuntimeException error) {
            RadarAiException safeError = new RadarAiException("PROVIDER_FAILURE",
                    "Configured AI provider failed unexpectedly", false, 1, error);
            recordFailure(company.id(), normalizedType, provider.providerId(), model, inputHash, safeError);
            return deterministic(company, normalizedType, input);
        }
    }

    private Analysis deterministic(Company company, String analysisType, PublicCompanyAnalysisInput input) {
        String inputHash = input.stableHash(DETERMINISTIC_PROVIDER, DETERMINISTIC_MODEL, promptVersion, schemaVersion);
        return store.findCachedAnalysis(company.id(), analysisType, inputHash, promptVersion, schemaVersion,
                DETERMINISTIC_PROVIDER, DETERMINISTIC_MODEL)
                .orElseGet(() -> store.saveAnalysis(company.id(), analysisType, inputHash, promptVersion,
                        schemaVersion, "DETERMINISTIC", DETERMINISTIC_PROVIDER, DETERMINISTIC_MODEL,
                        withSourceUrls(scoringService.score(company), input.suppliedSourceUrls())));
    }

    private AnalysisPayload merge(AnalysisPayload deterministic, RadarAiOutput ai) {
        List<String> traction = additive(deterministic.tractionSignals(), ai.tractionSignals());
        List<String> market = additive(deterministic.marketSignals(), ai.marketSignals());
        return new AnalysisPayload(
                preferMeaningful(ai.summary(), deterministic.summary()),
                preferMeaningful(ai.sector(), deterministic.sector()),
                preferMeaningful(ai.problem(), deterministic.problem()),
                preferMeaningful(ai.solution(), deterministic.solution()),
                preferMeaningful(ai.businessModel(), deterministic.businessModel()),
                preferMeaningful(ai.stage(), deterministic.stage()),
                additive(deterministic.founders(), ai.founders()),
                preferMeaningful(ai.fundingSummary(), deterministic.fundingSummary()),
                additive(deterministic.likelyInvestors(), ai.investors()),
                additive(deterministic.trendTags(), ai.categories()),
                additive(deterministic.monitoringTriggers(), ai.watchTriggers()),
                additive(deterministic.facts(), ai.facts()),
                additive(deterministic.inferences(), ai.inferences()),
                additive(deterministic.whyInteresting(), ai.interestingSignals()),
                additive(deterministic.momentumSignals(), combine(ai.tractionSignals(), ai.marketSignals())),
                traction,
                additive(deterministic.technicalDifferentiation(), ai.technicalDifferentiation()),
                market,
                additive(deterministic.risks(), ai.risks()),
                additive(deterministic.bullCase(), ai.bullCase()),
                additive(deterministic.bearCase(), ai.bearCase()),
                additive(deterministic.unansweredQuestions(), ai.unansweredQuestions()),
                preferMeaningful(ai.whyItMatters(), deterministic.whyItMatters()),
                deterministic.whyYouShouldCare(),
                preferMeaningful(ai.investmentAccessibility(), deterministic.investmentAccessibility()),
                deterministic.careerAngle(),
                additive(deterministic.sourceUrls(), ai.sourceUrls()),
                preferMeaningful(ai.confidence(), deterministic.confidence()),
                additive(deterministic.radarScoreInputs(), ai.radarScoreInputs()),
                deterministic.personalScoreInputs(), deterministic.radarDimensions(), deterministic.radarScore(),
                deterministic.personalScore());
    }

    private AnalysisPayload withSourceUrls(AnalysisPayload payload, List<String> sourceUrls) {
        return new AnalysisPayload(payload.summary(), payload.sector(), payload.problem(), payload.solution(),
                payload.businessModel(), payload.stage(), payload.founders(), payload.fundingSummary(), payload.likelyInvestors(),
                payload.trendTags(), payload.monitoringTriggers(), payload.facts(), payload.inferences(),
                payload.whyInteresting(), payload.momentumSignals(), payload.tractionSignals(),
                payload.technicalDifferentiation(), payload.marketSignals(), payload.risks(), payload.bullCase(),
                payload.bearCase(), payload.unansweredQuestions(), payload.whyItMatters(), payload.whyYouShouldCare(),
                payload.investmentAccessibility(), payload.careerAngle(), sourceUrls, payload.confidence(),
                payload.radarScoreInputs(), payload.personalScoreInputs(), payload.radarDimensions(),
                payload.radarScore(), payload.personalScore());
    }

    private static String preferMeaningful(String candidate, String fallback) {
        return meaningful(candidate) ? candidate.trim() : fallback;
    }

    private static boolean meaningful(String value) {
        if (value == null || value.isBlank()) return false;
        String normalized = value.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s._-]+", " ");
        return !normalized.equals("unknown")
                && !normalized.startsWith("unknown from")
                && !normalized.equals("n/a")
                && !normalized.equals("na")
                && !normalized.equals("none")
                && !normalized.equals("not available")
                && !normalized.equals("not provided")
                && !normalized.equals("insufficient information");
    }

    private static List<String> additive(List<String> existing, List<String> candidate) {
        List<String> values = new ArrayList<>();
        if (existing != null) values.addAll(existing);
        if (candidate != null) values.addAll(candidate.stream().filter(RadarAnalysisService::meaningful).toList());
        return values.stream().filter(RadarAnalysisService::meaningful).map(String::trim).distinct().toList();
    }

    private void recordConfigurationFailure(Company company, String analysisType, PublicCompanyAnalysisInput input,
            String errorType, String message) {
        String model = "unconfigured";
        String inputHash = input.stableHash(configuredProvider, model, promptVersion, schemaVersion);
        recordFailure(company.id(), analysisType, configuredProvider, model, inputHash,
                new RadarAiException(errorType, message, false, 0));
    }

    private void recordFailure(long companyId, String analysisType, String provider, String model,
            String inputHash, RadarAiException error) {
        store.recordAiAttempt(companyId, analysisType, provider, model, inputHash, promptVersion, schemaVersion,
                "FAILED", error.errorType(), error.getMessage(), Math.max(0, error.attempts() - 1), null, null,
                null, error.httpStatus(), error.providerErrorType(), error.providerErrorCode());
        log.warn("radar_ai_fallback companyId={} provider={} model={} success=false retryCount={} errorType={}",
                companyId, provider, model, Math.max(0, error.attempts() - 1), error.errorType());
    }

    private static List<String> combine(List<String> first, List<String> second) {
        List<String> values = new ArrayList<>();
        if (first != null) values.addAll(first);
        if (second != null) values.addAll(second);
        return values.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
    }

    private static String normalizeAnalysisType(String analysisType) {
        String value = analysisType == null ? "" : analysisType.trim().toUpperCase(Locale.ROOT);
        if (!List.of("RADAR", "DEEP_DIVE").contains(value)) {
            throw new IllegalArgumentException("Unsupported Radar analysis type: " + analysisType);
        }
        return value;
    }
}
