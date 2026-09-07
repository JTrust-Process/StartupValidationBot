package com.startupvalidationbot.radar.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.startupvalidationbot.radar.RadarDomain.Company;
import com.startupvalidationbot.radar.RadarDomain.JobResult;
import com.startupvalidationbot.radar.RadarStore;
import com.startupvalidationbot.radar.RadarStore.JobStart;
import com.startupvalidationbot.radar.ai.RadarAiRunBudget;
import com.startupvalidationbot.radar.service.RadarDiscoveryService.DiscoveryResult;
import com.startupvalidationbot.radar.service.RadarDigestService.DigestResult;
import com.startupvalidationbot.offering.OfferingDiscoveryService;
import com.startupvalidationbot.diligence.AutonomousDiligenceService;

@Service
public class RadarJobService {
    private final RadarStore store;
    private final RadarDiscoveryService discoveryService;
    private final RadarAnalysisService analysisService;
    private final RadarTrendService trendService;
    private final RadarDigestService digestService;
    private final OfferingDiscoveryService offeringDiscoveryService;
    private final AutonomousDiligenceService autonomousDiligenceService;
    private final Duration jobLease;

    @Autowired
    public RadarJobService(RadarStore store, RadarDiscoveryService discoveryService,
            RadarAnalysisService analysisService, RadarTrendService trendService, RadarDigestService digestService,
            OfferingDiscoveryService offeringDiscoveryService,
            AutonomousDiligenceService autonomousDiligenceService,
            @Value("${radar.job-lease-minutes:120}") long jobLeaseMinutes) {
        this.store = store;
        this.discoveryService = discoveryService;
        this.analysisService = analysisService;
        this.trendService = trendService;
        this.digestService = digestService;
        this.offeringDiscoveryService = offeringDiscoveryService;
        this.autonomousDiligenceService = autonomousDiligenceService;
        this.jobLease = Duration.ofMinutes(Math.max(15, Math.min(jobLeaseMinutes, 360)));
    }

    RadarJobService(RadarStore store, RadarDiscoveryService discoveryService,
            RadarAnalysisService analysisService, RadarTrendService trendService, RadarDigestService digestService,
            OfferingDiscoveryService offeringDiscoveryService, long jobLeaseMinutes) {
        this(store, discoveryService, analysisService, trendService, digestService, offeringDiscoveryService,
                null, jobLeaseMinutes);
    }

    public JobResult run(String jobType, String requestedKey, boolean scheduled) {
        String normalized = jobType == null ? "" : jobType.trim().toLowerCase();
        String key = requestedKey == null || requestedKey.isBlank()
                ? (scheduled ? LocalDate.now().toString() : UUID.randomUUID().toString())
                : requestedKey.trim();
        if (!List.of("discovery", "offering-discovery", "autonomous-diligence", "watchlist", "trends", "digest", "digest-preview").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported Radar job: " + jobType);
        }
        JobStart start = store.beginJob(normalized, key, jobLease);
        if (!start.acquired()) {
            return new JobResult(true, normalized, key, true, 0, 0, 0, 0, List.of(),
                    start.duplicate() ? "Skipped duplicate job run." : "Skipped because this job type is running.");
        }

        try {
            JobResult result = switch (normalized) {
                case "discovery" -> discovery(normalized, key);
                case "offering-discovery" -> offeringDiscovery(normalized, key);
                case "autonomous-diligence" -> autonomousDiligence(normalized, key);
                case "watchlist" -> watchlist(normalized, key);
                case "trends" -> trends(normalized, key);
                case "digest" -> digest(normalized, key, true);
                case "digest-preview" -> digest(normalized, key, false);
                default -> throw new IllegalStateException("Unexpected job type");
            };
            store.completeJob(normalized, key, start.leaseToken(), result.ok() ? "COMPLETED" : "FAILED", result,
                    result.errors().isEmpty() ? null : String.join("; ", result.errors()));
            return result;
        } catch (RuntimeException error) {
            store.completeJob(normalized, key, start.leaseToken(), "FAILED",
                    java.util.Map.of("message", error.getMessage()), error.getMessage());
            throw error;
        }
    }

    private JobResult discovery(String jobType, String key) {
        DiscoveryResult result = discoveryService.discoverEnabledSources();
        return new JobResult(result.errors().isEmpty(), jobType, key, false, result.processed(), result.created(),
                result.updated(), result.errors().size(), result.errors(), result.diagnostics(),
                result.diagnostics().isEmpty() ? "Discovery run completed."
                        : "Discovery run completed with " + result.diagnostics().size() + " skipped candidate(s).");
    }

    private JobResult offeringDiscovery(String jobType, String key) {
        com.startupvalidationbot.offering.OfferingDomain.DiscoveryResult result = offeringDiscoveryService.discover();
        return new JobResult(result.errors() == 0, jobType, key, false, result.recordsInspected(), result.created(),
                result.updated(), result.errors(), result.errorMessages(),
                "Offering discovery completed: " + result.confirmed() + " confirmed, "
                        + result.possible() + " possible matches.");
    }

    private JobResult autonomousDiligence(String jobType, String key) {
        if (autonomousDiligenceService == null) {
            throw new IllegalStateException("Autonomous diligence is not configured.");
        }
        com.startupvalidationbot.diligence.DiligenceDomain.RunResult result = autonomousDiligenceService.run();
        var campaign = result.campaignDiscovery();
        return new JobResult(result.errors().isEmpty(), jobType, key, false, result.companiesConsidered(),
                result.packetsReady(), result.packetsPartial() + result.needsReview(), result.errors().size(),
                result.errors(), List.of(
                        "companiesConsidered=" + result.companiesConsidered(),
                        "offeringsConsidered=" + result.offeringsConsidered(),
                        "identitiesResolved=" + result.identitiesResolved(),
                        "campaignsResolved=" + result.campaignsResolved(),
                        "packetsReady=" + result.packetsReady(),
                        "packetsPartial=" + result.packetsPartial(),
                        "needsReview=" + result.needsReview(),
                        "platformErrors=" + result.platformErrors(),
                        "aiFallbacks=" + result.aiFallbacks(),
                        "emailsQueued=" + result.emailsQueued(),
                        "emailsSent=" + result.emailsSent(),
                        "emailsFailed=" + result.emailsFailed(),
                        "campaignCompaniesEligible=" + campaign.companiesEligible(),
                        "campaignCompaniesSearched=" + campaign.companiesSearched(),
                        "wefunderSearches=" + campaign.wefunderSearches(),
                        "republicSearches=" + campaign.republicSearches(),
                        "startEngineSearches=" + campaign.startEngineSearches(),
                        "campaignCandidatesFound=" + campaign.candidatesFound(),
                        "campaignConfirmed=" + campaign.campaignsConfirmed(),
                        "campaignPossible=" + campaign.campaignsPossible(),
                        "campaignRejected=" + campaign.campaignsRejected(),
                        "campaignCacheHits=" + campaign.cacheHits(),
                        "campaignDiscoveryErrors=" + campaign.errors()),
                "Autonomous diligence completed: " + result.packetsReady() + " ready, "
                        + result.packetsPartial() + " partial, " + result.needsReview() + " need review.");
    }

    private JobResult watchlist(String jobType, String key) {
        List<Company> watched = store.listWatchedCompanies();
        int errors = 0;
        RadarAiRunBudget budget = analysisService.newRunBudget();
        for (Company company : watched) {
            try {
                analysisService.analyze(company, "RADAR", budget);
            } catch (RuntimeException error) {
                errors++;
            }
        }
        return new JobResult(errors == 0, jobType, key, false, watched.size(), 0, watched.size() - errors,
                errors, errors == 0 ? List.of() : List.of(errors + " watchlist analyses failed."),
                "Watchlist refresh completed.");
    }

    private JobResult trends(String jobType, String key) {
        int count = trendService.rebuild();
        return new JobResult(true, jobType, key, false, store.listCompanies().size(), count, 0, 0, List.of(),
                "Trend clusters rebuilt.");
    }

    private JobResult digest(String jobType, String key, boolean send) {
        DigestResult result = digestService.generateAndMaybeSend(send);
        List<String> errors = result.error() == null ? List.of() : List.of(result.error());
        return new JobResult(result.ok(), jobType, key, false, 1, result.sent() ? 1 : 0,
                result.sent() ? 0 : 1, errors.size(), errors,
                result.sent() ? "Combined digest sent." : "Combined digest preview generated.");
    }
}
