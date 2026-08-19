package com.startupvalidationbot.radar;

import java.net.URI;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

public final class CompanyIdentity {
    private static final Set<String> LEGAL_SUFFIXES = Set.of(
            "inc", "incorporated", "llc", "ltd", "limited", "corp", "corporation", "company", "co", "plc");

    /**
     * Hosts that publish or aggregate startup news but are never a startup's own website.
     * A discovery whose link points at one of these must not contribute a company domain: the
     * domain column is UNIQUE, so accepting a publisher host would collapse every article from
     * that publisher into a single company record.
     */
    private static final Set<String> NON_COMPANY_HOSTS = Set.of(
            "techcrunch.com", "producthunt.com", "ycombinator.com", "news.ycombinator.com",
            "crunchbase.com", "medium.com", "substack.com", "linkedin.com", "twitter.com", "x.com",
            "github.com", "youtube.com", "facebook.com", "instagram.com", "reddit.com",
            "wikipedia.org", "businesswire.com", "prnewswire.com", "globenewswire.com",
            "bloomberg.com", "reuters.com", "forbes.com", "axios.com", "theinformation.com",
            "venturebeat.com", "theverge.com", "wired.com", "sifted.eu", "eu-startups.com",
            "sequoiacap.com", "a16z.com", "accel.com", "benchmark.com", "foundersfund.com",
            "generalcatalyst.com", "lightspeedvp.com", "bvp.com", "indexventures.com",
            "greylock.com", "thrivecap.com", "gv.com", "nea.com", "menlovc.com");

    private CompanyIdentity() {
    }

    public static String normalizeName(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace('&', ' ')
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        if (normalized.isEmpty()) {
            return normalized;
        }
        String[] parts = normalized.split("\\s+");
        int end = parts.length;
        while (end > 1 && LEGAL_SUFFIXES.contains(parts[end - 1])) {
            end--;
        }
        return String.join(" ", Arrays.copyOf(parts, end));
    }

    /** True when the host (or its registrable parent) is a publisher/aggregator rather than a startup. */
    public static boolean isNonCompanyHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String candidate = host.toLowerCase(Locale.ROOT);
        if (candidate.startsWith("www.")) {
            candidate = candidate.substring(4);
        }
        while (candidate.contains(".")) {
            if (NON_COMPANY_HOSTS.contains(candidate)) {
                return true;
            }
            candidate = candidate.substring(candidate.indexOf('.') + 1);
        }
        return false;
    }

    public static String normalizeDomain(String websiteUrl) {
        if (websiteUrl == null || websiteUrl.isBlank()) {
            return null;
        }
        try {
            String candidate = websiteUrl.trim();
            URI uri = URI.create(candidate.contains("://") ? candidate : "https://" + candidate);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
            host = host.toLowerCase(Locale.ROOT);
            String apex = host.startsWith("www.") ? host.substring(4) : host;
            return isNonCompanyHost(apex) ? null : apex;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
