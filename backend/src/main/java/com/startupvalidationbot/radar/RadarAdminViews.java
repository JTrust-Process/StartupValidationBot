package com.startupvalidationbot.radar;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.startupvalidationbot.radar.RadarDomain.Analysis;
import com.startupvalidationbot.radar.RadarDomain.Company;
import com.startupvalidationbot.radar.RadarDomain.Source;
import com.startupvalidationbot.radar.RadarDomain.Trend;

public final class RadarAdminViews {
    private RadarAdminViews() {
    }

    public record JobRunStatus(String jobType, String status, LocalDateTime startedAt,
            LocalDateTime completedAt, String errorMessage) {
    }

    public record SystemStatus(boolean databaseHealthy, JobRunStatus lastDiscoveryRun,
            LocalDateTime lastEnrichmentRun, JobRunStatus lastWatchlistRefresh, JobRunStatus lastTrendRun,
            LocalDateTime lastDigest, List<JobRunStatus> recentJobFailures, long discoveriesProcessed,
            long aiCalls, long aiCacheHits, long aiFailures, boolean aiEnabled, String aiProvider,
            String routineModel, String deepDiveModel, Map<String, Boolean> integrations) {
    }

    public record AdminSource(long id, String sourceKey, String sourceType, String name, String url,
            boolean enabled, LocalDateTime lastCheckedAt, String lastStatus, String lastError) {
        public static AdminSource from(Source source) {
            return new AdminSource(source.id(), source.sourceKey(), source.sourceType(), source.name(), source.url(),
                    source.enabled(), source.lastCheckedAt(), source.lastStatus(), source.lastError());
        }
    }

    public record ExportSource(long id, String sourceKey, String sourceType, String name, String url,
            boolean enabled, LocalDateTime lastCheckedAt, String lastStatus, String lastError) {
    }

    public record ExportDiscovery(long id, long companyId, long sourceId, String externalId, String sourceUrl,
            String rawTextHash, LocalDateTime discoveredAt, LocalDateTime lastSeenAt) {
    }

    public record ExportSnapshot(long id, long companyId, Long sourceId, LocalDateTime capturedAt,
            String inputHash, JsonNode snapshot, List<String> notableChanges) {
    }

    public record ExportAnalysis(long companyId, String inputHash, String status, Analysis analysis) {
    }

    public record ExportWatchlist(long companyId, String status, String notes, LocalDateTime nextReviewAt,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record ExportResearchSource(long id, long companyId, String sourceType, String title, String url,
            LocalDateTime sourceDate, boolean fact, LocalDateTime createdAt) {
    }

    public record RadarExport(String schemaVersion, LocalDateTime exportedAt, List<Company> companies,
            List<ExportDiscovery> discoveries, List<ExportSource> publicSources, List<ExportSnapshot> snapshots,
            List<ExportAnalysis> analyses, List<ExportWatchlist> watchlist, List<Trend> trends,
            List<ExportResearchSource> researchSourceReferences) {
    }
}
