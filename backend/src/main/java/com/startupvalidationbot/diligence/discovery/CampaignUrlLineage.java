package com.startupvalidationbot.diligence.discovery;

import java.util.Map;
import com.startupvalidationbot.diligence.DiligenceDomain.PlatformCampaign;
import com.startupvalidationbot.diligence.CampaignVerification.UrlEvidence;
import com.startupvalidationbot.offering.ExplicitCampaignLinks;

/** A syntactically valid campaign path is not proof that the URL was observed. */
public final class CampaignUrlLineage {
    private CampaignUrlLineage() { }

    public static UrlEvidence evidence(String url, Map<String, String> facts, PlatformCampaign previous, String provenance) {
        if ("AMBIGUOUS".equals(facts.get("_secCampaign.state")) || "AMBIGUOUS".equals(facts.get("_issuerCampaign.state")))
            return new UrlEvidence(null, "CONFLICTING", "UNKNOWN", null, null, "Multiple or conflicting observed campaign links require review.");
        java.util.Set<String> current = new java.util.LinkedHashSet<>();
        for (String prefix : new String[] { "_secCampaign", "_issuerCampaign" }) {
            String observed = facts.get(prefix + ".url");
            if ("CONFIRMED".equals(facts.get(prefix + ".state")) && explicitSource(prefix, facts) && observed != null) {
                String normalized = ExplicitCampaignLinks.canonical(observed);
                if (normalized != null) current.add(normalized);
            }
        }
        if (current.size() > 1) return new UrlEvidence(null, "CONFLICTING", "UNKNOWN", null, null, "Conflicting exact source-backed URLs require offering-specific review.");
        String candidate = url == null && previous != null ? previous.campaignUrl() : url;
        String canonical = candidate == null ? null : ExplicitCampaignLinks.canonical(candidate);
        if (canonical == null) return new UrlEvidence(null, url == null ? "UNVERIFIED" : "REJECTED", "UNKNOWN", null, null,
                "No canonical campaign link established for a supported public platform; no URL is synthesized.");
        for (String prefix : new String[] { "_secCampaign", "_issuerCampaign" }) {
            if ("CONFIRMED".equals(facts.get(prefix + ".state")) && canonical.equals(facts.get(prefix + ".url")) && explicitSource(prefix, facts)) {
                String classification = prefix.equals("_secCampaign") ? "SEC_FILED_FACT" : facts.get(prefix + ".source");
                return new UrlEvidence(canonical, "EXPLICIT_OBSERVED", classification, facts.get(prefix + ".sourceUrl"),
                        time(facts.get(prefix + ".observedAt")), "Exact URL observed in retained source evidence; page verification is separate.");
            }
        }
        if ("MANUAL".equals(provenance) && canonical.equals(facts.get("offeringUrl")))
            return new UrlEvidence(canonical, "EXPLICIT_OBSERVED", "USER_SUPPLIED_URL", canonical, null, "Owner supplied the exact URL; issuer matching still required.");
        if (previous != null && canonical.equals(previous.campaignUrl()) && observedSource(previous.campaignUrlSource()))
            return new UrlEvidence(canonical, previous.facts().containsKey("_urlSource") ? "EXPLICIT_OBSERVED" : "HISTORICAL", previous.campaignUrlSource(),
                    previous.facts().get("_urlSource"), previous.lastCheckedAt(),
                    "Exact URL retained from public listing/history; this alone does not verify the page.");
        return new UrlEvidence(canonical, "UNVERIFIED", "UNKNOWN", null, null, "A valid-looking URL has no retained observed lineage.");
    }

    private static java.time.LocalDateTime time(String value) {
        try { return value == null ? null : java.time.LocalDateTime.parse(value); }
        catch (RuntimeException error) { return null; }
    }

    public static boolean allowed(String url, Map<String, String> facts, PlatformCampaign previous, String provenance) {
        String canonical = url == null ? null : ExplicitCampaignLinks.canonical(url);
        if (canonical == null) return false;
        if (previous != null && canonical.equals(previous.campaignUrl()) && previous.lastVerifiedAt() != null
                && observedSource(previous.campaignUrlSource())) return true;
        for (String prefix : new String[] { "_secCampaign", "_issuerCampaign" }) {
            if ("CONFIRMED".equals(facts.get(prefix + ".state")) && canonical.equals(facts.get(prefix + ".url"))
                    && explicitSource(prefix, facts)) return true;
        }
        return "MANUAL".equals(provenance) && canonical.equals(facts.get("offeringUrl"));
    }

    private static boolean explicitSource(String prefix, Map<String, String> facts) {
        String sourceUrl = facts.get(prefix + ".sourceUrl");
        if (sourceUrl == null || sourceUrl.isBlank()) return false;
        if (prefix.equals("_secCampaign") || "SEC_FILED_FACT".equals(facts.get(prefix + ".source"))) {
            try { com.startupvalidationbot.offering.SecFilingClient.requireOfficialUrl(sourceUrl); return true; }
            catch (IllegalArgumentException error) { return false; }
        }
        try {
            var uri = java.net.URI.create(sourceUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getPort() != -1
                    || uri.getHost().equals("localhost") || uri.getHost().endsWith(".internal") || uri.getHost().matches("[0-9.]+")) return false;
        } catch (RuntimeException error) { return false; }
        return java.util.Set.of("SEC_FILED_FACT", "ISSUER_WEBSITE_CLAIM", "PUBLIC_DIRECTORY", "USER_SUPPLIED_URL")
                .contains(facts.getOrDefault(prefix + ".source", "UNKNOWN"));
    }

    public static boolean observedSource(String source) {
        if (source == null) return false;
        return source.equals("SEC_OFFERING_URL") || source.equals("PUBLIC_CAMPAIGN_URL")
                || source.equals("PUBLIC_LIVE_DIRECTORY")
                || source.equals("ISSUER_WEBSITE_CLAIM") || source.equals("USER_SUPPLIED_URL")
                || sourceUrl(source);
    }

    private static boolean sourceUrl(String value) {
        try {
            var uri = java.net.URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null && uri.getPort() == -1
                    && (ExplicitCampaignLinks.canonical(value) != null
                    || java.util.Set.of("republic.com", "www.republic.com", "wefunder.com", "www.wefunder.com", "startengine.com", "www.startengine.com", "www.sec.gov", "sec.gov")
                    .contains(uri.getHost()));
        } catch (RuntimeException error) { return false; }
    }
}
