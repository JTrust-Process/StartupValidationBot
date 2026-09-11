package com.startupvalidationbot.diligence.discovery;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class CampaignDiscoveryDomain {
    private CampaignDiscoveryDomain() { }

    public enum IdentityStatus { CONFIRMED, POSSIBLE, AMBIGUOUS, REJECTED }

    public record CampaignIdentity(long companyId, Long offeringId, String companyName,
            String companyWebsite, String companyDomain, List<String> aliases, String issuerName,
            String issuerCik, String issuerWebsite, String intermediaryName, String filingUrl,
            String knownPlatform, boolean platformConfirmedBySec, String fingerprint) { }

    public record CampaignCandidate(String platform, String campaignUrl, String issuerName,
            String issuerDomain, String status, String platformIdentifier, String evidenceSource,
            int discoveryConfidence, Map<String, String> metadata) { }

    public record IdentityDecision(IdentityStatus status, int confidence, String reason) { }

    public record DiscoveryAttempt(List<CampaignCandidate> candidates, String capability,
            String error) {
        public static DiscoveryAttempt success(List<CampaignCandidate> candidates, String capability) {
            return new DiscoveryAttempt(List.copyOf(candidates), capability, null);
        }

        public static DiscoveryAttempt failed(String capability, String error) {
            return new DiscoveryAttempt(List.of(), capability, error);
        }
    }

    public record PlatformRun(String platform, String capability, String status, int requests,
            int candidates, int resolved, String error) { }

    public record RunResult(int companiesEligible, int companiesSearched, int wefunderSearches,
            int republicSearches, int startEngineSearches, int candidatesFound,
            int campaignsConfirmed, int campaignsPossible, int campaignsRejected, int cacheHits,
            int errors, List<PlatformRun> platforms) {
        public static RunResult empty() {
            return new RunResult(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of());
        }
    }

    public record PlatformDiagnostic(String platform, String capability, LocalDateTime lastCheckedAt,
            LocalDateTime lastSuccessAt, LocalDateTime lastFailureAt, String status, int requests,
            int candidates, int resolved, String error) { }
}
