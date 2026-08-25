package com.startupvalidationbot.radar;

import java.time.LocalDateTime;
import java.util.List;

import com.startupvalidationbot.radar.RadarDomain.Company;

/** API projections for the Phase 2 intelligence surface. */
public final class RadarIntelViews {
    private RadarIntelViews() {
    }

    public record InterestView(String label, int weight, List<String> keywords) {
    }

    public record InterestProfileView(List<InterestView> interests, LocalDateTime updatedAt) {
    }

    public record RelevanceExplanation(int score, List<String> matchedInterests, List<String> reasons) {
    }

    public record CompanyChangeView(long id, long companyId, String companyName, String changeType,
            String significance, String summary, String previousValue, String currentValue,
            String whyItMatters, LocalDateTime detectedAt) {
    }

    public record SimilarCompanyView(long companyId, String name, int score, String relationship,
            List<String> reasons, List<String> categories, int radarScore, int personalScore) {
    }

    public record TrendView(long id, String key, String name, String summary, String whyItMatters,
            String confidence, int companyCount, int recentDiscoveries, int priorDiscoveries,
            String velocityDirection, String velocityNote, int momentumScore, List<Company> companies) {
    }

    /** One Radar Home section. `kind` drives the card layout on the client. */
    public record HomeSection(String key, String title, String subtitle, String kind,
            List<HomeCompanyCard> companies, List<CompanyChangeView> changes, List<TrendView> trends) {

        public static HomeSection ofCompanies(String key, String title, String subtitle,
                List<HomeCompanyCard> companies) {
            return new HomeSection(key, title, subtitle, "COMPANIES", companies, List.of(), List.of());
        }

        public static HomeSection ofChanges(String key, String title, String subtitle,
                List<CompanyChangeView> changes) {
            return new HomeSection(key, title, subtitle, "CHANGES", List.of(), changes, List.of());
        }

        public static HomeSection ofTrends(String key, String title, String subtitle, List<TrendView> trends) {
            return new HomeSection(key, title, subtitle, "TRENDS", List.of(), List.of(), trends);
        }
    }

    public record HomeCompanyCard(long id, String name, String description, String sector,
            List<String> categories, String accelerator, String acceleratorBatch, int radarScore,
            int personalScore, int sourceCount, boolean watched, LocalDateTime firstSeenAt,
            LocalDateTime lastSeenAt, List<String> whyItMatters, List<String> whyYouMightCare,
            String highlight) {
    }

    public record RadarHome(LocalDateTime generatedAt, int totalCompanies, int newSinceYesterday,
            int meaningfulChanges, List<HomeSection> sections) {
    }
}
