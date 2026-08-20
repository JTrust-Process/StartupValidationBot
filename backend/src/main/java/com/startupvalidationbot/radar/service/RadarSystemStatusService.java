package com.startupvalidationbot.radar.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.startupvalidationbot.radar.RadarAdminViews.SystemStatus;
import com.startupvalidationbot.radar.RadarAdminViews.JobRunStatus;
import com.startupvalidationbot.radar.RadarStore;

@Service
public class RadarSystemStatusService {
    private final RadarStore store;
    private final boolean aiEnabled;
    private final String provider;
    private final String routineModel;
    private final String deepDiveModel;
    private final boolean aiCredentialConfigured;
    private final boolean productHuntConfigured;
    private final boolean dealScoutConfigured;
    private final boolean emailConfigured;
    private final boolean browserAdminConfigured;
    private final boolean workerAuthConfigured;

    public RadarSystemStatusService(RadarStore store,
            @Value("${radar.ai.enabled:false}") boolean aiEnabled,
            @Value("${radar.ai.provider:groq}") String provider,
            @Value("${radar.ai.model:openai/gpt-oss-20b}") String routineModel,
            @Value("${radar.ai.deep-dive-model:openai/gpt-oss-120b}") String deepDiveModel,
            @Value("${radar.ai.groq-api-key:}") String aiCredential,
            @Value("${radar.product-hunt-token:}") String productHuntToken,
            @Value("${radar.deal-scout-run-url:}") String dealScoutRunUrl,
            @Value("${radar.email-send-url:}") String emailSendUrl,
            @Value("${radar.auth.admin-password-hash:}") String passwordHash,
            @Value("${radar.auth.browser-origin:}") String browserOrigin,
            @Value("${app.allowed-origins:}") String allowedOrigins,
            @Value("${radar.run-token:}") String runToken) {
        this.store = store;
        this.aiEnabled = aiEnabled;
        this.provider = provider;
        this.routineModel = routineModel;
        this.deepDiveModel = deepDiveModel;
        this.aiCredentialConfigured = present(aiCredential);
        this.productHuntConfigured = present(productHuntToken);
        this.dealScoutConfigured = present(dealScoutRunUrl);
        this.emailConfigured = present(emailSendUrl);
        this.browserAdminConfigured = present(passwordHash) && present(browserOrigin)
                && java.util.Arrays.stream(allowedOrigins.split(",")).map(String::trim)
                        .anyMatch(browserOrigin::equals);
        this.workerAuthConfigured = present(runToken);
    }

    public SystemStatus status() {
        Map<String, Boolean> integrations = new LinkedHashMap<>();
        integrations.put("browserAdmin", browserAdminConfigured);
        integrations.put("workerAuthentication", workerAuthConfigured);
        integrations.put("aiCredentials", aiCredentialConfigured);
        integrations.put("productHunt", productHuntConfigured);
        integrations.put("dealScoutDigest", dealScoutConfigured);
        integrations.put("emailDelivery", emailConfigured);
        return new SystemStatus(store.databaseHealthy(), sanitize(store.latestJobRun("discovery").orElse(null)),
                store.latestEnrichmentAt(), sanitize(store.latestJobRun("watchlist").orElse(null)),
                sanitize(store.latestJobRun("trends").orElse(null)), store.latestDigestAt(),
                store.recentJobFailures(10).stream().map(RadarSystemStatusService::sanitize).toList(),
                store.discoveryCount(), store.aiCallCount(), store.aiAttemptCount("CACHE_HIT"),
                store.aiAttemptCount("FAILED"), aiEnabled, provider, routineModel, deepDiveModel,
                Map.copyOf(integrations));
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static JobRunStatus sanitize(JobRunStatus status) {
        if (status == null) return null;
        return new JobRunStatus(status.jobType(), status.status(), status.startedAt(), status.completedAt(),
                status.errorMessage() == null ? null : "Failure recorded; inspect server logs.");
    }
}
