package com.startupvalidationbot.diligence.discovery;

import java.util.Map;
import com.startupvalidationbot.diligence.DiligenceDomain.PlatformCampaign;
import com.startupvalidationbot.offering.ExplicitCampaignLinks;

/** A syntactically valid campaign path is not proof that the URL was observed. */
public final class CampaignUrlLineage {
    private CampaignUrlLineage() { }

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
        if (prefix.equals("_secCampaign")) {
            try { com.startupvalidationbot.offering.SecFilingClient.requireOfficialUrl(sourceUrl); return true; }
            catch (IllegalArgumentException error) { return false; }
        }
        return java.util.Set.of("SEC_FILED_FACT", "ISSUER_WEBSITE_CLAIM", "PUBLIC_DIRECTORY", "USER_SUPPLIED_URL")
                .contains(facts.getOrDefault(prefix + ".source", "UNKNOWN"));
    }

    public static boolean observedSource(String source) {
        if (source == null) return false;
        return source.equals("SEC_OFFERING_URL") || source.equals("PUBLIC_CAMPAIGN_URL")
                || source.equals("PUBLIC_LIVE_DIRECTORY")
                || source.equals("ISSUER_WEBSITE_CLAIM") || source.equals("USER_SUPPLIED_URL")
                || source.startsWith("https://");
    }
}
