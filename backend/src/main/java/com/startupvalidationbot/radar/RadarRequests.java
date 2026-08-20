package com.startupvalidationbot.radar;

import java.time.LocalDateTime;
import java.util.List;

import com.startupvalidationbot.radar.RadarDomain.EvidenceClassification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class RadarRequests {
    private RadarRequests() {
    }

    public record ManualDiscovery(@NotBlank @Size(max = 300) String companyName,
            @Size(max = 1200) String websiteUrl, @Size(max = 8000) String description,
            @Size(max = 160) String sector, List<@Size(max = 120) String> categories,
            @Size(max = 240) String headquarters, Integer foundedYear, @Size(max = 1200) String sourceUrl,
            @Size(max = 500) String externalId, @Size(max = 40000) String rawText,
            EvidenceClassification evidenceClassification) {

        public ManualDiscovery {
            evidenceClassification = evidenceClassification == null ? EvidenceClassification.UNKNOWN
                    : evidenceClassification;
        }

        public ManualDiscovery(String companyName, String websiteUrl, String description, String sector,
                List<String> categories, String headquarters, Integer foundedYear, String sourceUrl,
                String externalId, String rawText) {
            this(companyName, websiteUrl, description, sector, categories, headquarters, foundedYear, sourceUrl,
                    externalId, rawText, EvidenceClassification.UNKNOWN);
        }
    }

    public record SourceUpsert(@NotBlank @Size(max = 160) String sourceKey,
            @NotBlank @Size(max = 40) String sourceType, @NotBlank @Size(max = 240) String name,
            @Size(max = 1200) String url, boolean enabled) {
    }

    public record WatchlistUpdate(@Size(max = 8000) String notes, LocalDateTime nextReviewAt) {
    }

    public record JobRun(@Size(max = 160) String idempotencyKey) {
    }
}
