package com.startupvalidationbot.radar;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class RadarDomain {
    private RadarDomain() {
    }

    public record Source(Long id, String sourceKey, String sourceType, String name, String url,
            String configJson, boolean enabled, LocalDateTime lastCheckedAt, String lastStatus, String lastError) {
    }

    public enum EvidenceClassification {
        PUBLIC_OFFICIAL,
        PUBLIC_NEWS,
        PRIVATE_USER,
        UNKNOWN;

        public boolean eligibleForExternalAnalysis() {
            return this == PUBLIC_OFFICIAL || this == PUBLIC_NEWS;
        }
    }

    public record Candidate(String sourceKey, String externalId, String companyName, String websiteUrl,
            String description, String sector, List<String> categories, String headquarters, Integer foundedYear,
            String sourceUrl, LocalDateTime publishedAt, String rawText, String accelerator,
            String acceleratorBatch, EvidenceClassification evidenceClassification) {

        public Candidate {
            evidenceClassification = evidenceClassification == null ? EvidenceClassification.UNKNOWN
                    : evidenceClassification;
        }

        public Candidate(String sourceKey, String externalId, String companyName, String websiteUrl,
                String description, String sector, List<String> categories, String headquarters,
                Integer foundedYear, String sourceUrl, LocalDateTime publishedAt, String rawText,
                String accelerator, String acceleratorBatch) {
            this(sourceKey, externalId, companyName, websiteUrl, description, sector, categories, headquarters,
                    foundedYear, sourceUrl, publishedAt, rawText, accelerator, acceleratorBatch,
                    EvidenceClassification.UNKNOWN);
        }

        /** Most sources carry no accelerator provenance; this keeps their call sites unchanged in spirit. */
        public Candidate(String sourceKey, String externalId, String companyName, String websiteUrl,
                String description, String sector, List<String> categories, String headquarters,
                Integer foundedYear, String sourceUrl, LocalDateTime publishedAt, String rawText) {
            this(sourceKey, externalId, companyName, websiteUrl, description, sector, categories, headquarters,
                    foundedYear, sourceUrl, publishedAt, rawText, "", "", EvidenceClassification.UNKNOWN);
        }

        public Candidate(String sourceKey, String externalId, String companyName, String websiteUrl,
                String description, String sector, List<String> categories, String headquarters,
                Integer foundedYear, String sourceUrl, LocalDateTime publishedAt, String rawText,
                EvidenceClassification evidenceClassification) {
            this(sourceKey, externalId, companyName, websiteUrl, description, sector, categories, headquarters,
                    foundedYear, sourceUrl, publishedAt, rawText, "", "", evidenceClassification);
        }
    }

    public record Company(Long id, String name, String domain, String websiteUrl, String description, String sector,
            List<String> categories, String headquarters, Integer foundedYear, List<String> aliases, int radarScore,
            int personalScore, String scoreReasoning, int sourceCount, LocalDateTime firstSeenAt,
            LocalDateTime lastSeenAt, boolean ignored, boolean watched, String accelerator,
            String acceleratorBatch) {

        /** Compact constructor for callers that predate accelerator provenance (tests and fixtures). */
        public Company(Long id, String name, String domain, String websiteUrl, String description, String sector,
                List<String> categories, String headquarters, Integer foundedYear, List<String> aliases,
                int radarScore, int personalScore, String scoreReasoning, int sourceCount,
                LocalDateTime firstSeenAt, LocalDateTime lastSeenAt, boolean ignored, boolean watched) {
            this(id, name, domain, websiteUrl, description, sector, categories, headquarters, foundedYear,
                    aliases, radarScore, personalScore, scoreReasoning, sourceCount, firstSeenAt, lastSeenAt,
                    ignored, watched, "", "");
        }
    }

    public record Analysis(Long id, String analysisType, String analysisOrigin, String provider, String model,
            String promptVersion, String schemaVersion, String summary, String sector, String problem,
            String solution, String businessModel, String stage, List<String> founders, String fundingSummary,
            List<String> likelyInvestors, List<String> trendTags, List<String> monitoringTriggers,
            List<String> facts, List<String> inferences, List<String> whyInteresting, List<String> momentumSignals,
            List<String> tractionSignals, List<String> technicalDifferentiation, List<String> marketSignals,
            List<String> risks, List<String> bullCase, List<String> bearCase, List<String> unansweredQuestions,
            String whyItMatters, String whyYouShouldCare, String investmentAccessibility, String careerAngle,
            List<String> sourceUrls, String confidence, List<String> radarScoreInputs,
            List<String> personalScoreInputs, Map<String, Integer> radarDimensions,
            int radarScore, int personalScore, LocalDateTime createdAt) {
    }

    public record Snapshot(Long id, LocalDateTime capturedAt, List<String> notableChanges, String snapshotJson) {
    }

    public record ResearchSource(Long id, String sourceType, String title, String url,
            LocalDateTime sourceDate, String excerpt, boolean fact,
            EvidenceClassification evidenceClassification) {
    }

    public record CompanyDetail(Company company, Analysis latestAnalysis, List<Snapshot> snapshots,
            List<ResearchSource> researchSources, String watchlistNotes, LocalDateTime nextReviewAt) {
    }

    public record Trend(Long id, String key, String name, String summary, int companyCount, int momentumScore,
            LocalDateTime periodStart, LocalDateTime periodEnd, List<Company> companies) {
    }

    public record JobResult(boolean ok, String jobType, String idempotencyKey, boolean duplicate, int processed,
            int created, int updated, int errorCount, List<String> errors, String message) {
    }
}
