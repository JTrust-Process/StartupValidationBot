package com.startupvalidationbot.radar;

import java.net.URI;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

public final class CompanyIdentity {
    private static final Set<String> LEGAL_SUFFIXES = Set.of(
            "inc", "incorporated", "llc", "ltd", "limited", "corp", "corporation", "company", "co", "plc");

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
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
