package com.startupvalidationbot.radar.ai;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.startupvalidationbot.radar.RadarDomain.Company;
import com.startupvalidationbot.radar.RadarDomain.ResearchSource;
import com.startupvalidationbot.radar.RadarStore;
import com.startupvalidationbot.radar.ai.PublicCompanyAnalysisInput.PublicSourceEvidence;

@Component
public class PublicRadarAnalysisInputFactory {
    private static final Set<String> ALLOWED_PUBLIC_SOURCE_TYPES = Set.of(
            "RSS", "PRODUCT_HUNT", "HACKER_NEWS", "MANUAL");
    private final RadarStore store;

    public PublicRadarAnalysisInputFactory(RadarStore store) {
        this.store = store;
    }

    public PublicCompanyAnalysisInput create(Company company) {
        List<ResearchSource> researchSources = store.listResearchSources(company.id()).stream()
                .filter(ResearchSource::fact)
                .filter(source -> source.evidenceClassification().eligibleForExternalAnalysis())
                .filter(source -> ALLOWED_PUBLIC_SOURCE_TYPES.contains(source.sourceType().toUpperCase(Locale.ROOT)))
                .filter(source -> isPublicWebUrl(source.url()))
                .toList();
        List<PublicSourceEvidence> sources = researchSources.stream()
                .map(source -> new PublicSourceEvidence(source.sourceType(), source.title(), source.url(),
                        truncate(source.excerpt(), 2_000)))
                .toList();
        List<String> publicText = researchSources.stream().map(ResearchSource::excerpt)
                .filter(value -> value != null && !value.isBlank()).toList();
        String publicDescription = publicText.stream().findFirst().orElse("");

        return new PublicCompanyAnalysisInput(company.id(), company.name(), company.domain(), company.websiteUrl(),
                publicDescription, company.sector(), company.categories(), company.headquarters(),
                company.foundedYear(), firstMatching(publicText, "accelerator", "y combinator", "batch"),
                matching(publicText, "launch", "launched", "accelerator", "batch"),
                matching(publicText, "funding", "funded", "raised", "seed", "series"),
                matching(publicText, "investor", "backed by", "venture"),
                matching(publicText, "revenue", "customer", "users", "growth", "contract", "pilot"),
                sources, company.sourceCount());
    }

    private static List<String> matching(List<String> values, String... keywords) {
        return values.stream().filter(value -> containsAny(value, keywords)).map(value -> truncate(value, 2_000))
                .distinct().limit(10).toList();
    }

    private static String firstMatching(List<String> values, String... keywords) {
        return values.stream().filter(value -> containsAny(value, keywords)).map(value -> truncate(value, 500))
                .findFirst().orElse("Unknown");
    }

    private static boolean containsAny(String value, String... keywords) {
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (normalized.contains(keyword)) return true;
        }
        return false;
    }

    private static boolean isPublicWebUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            return ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    && host != null && !host.isBlank() && !"localhost".equalsIgnoreCase(host)
                    && !host.startsWith("127.") && !host.startsWith("10.") && !host.startsWith("192.168.");
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
