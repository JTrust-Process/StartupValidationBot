package com.startupvalidationbot.diligence.discovery;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import com.startupvalidationbot.offering.ExplicitCampaignLinks;
import com.startupvalidationbot.offering.IntermediaryRegistry;
import com.startupvalidationbot.offering.OfferingDomain.Offering;
import com.startupvalidationbot.offering.OfferingDomain.MatchStatus;

@Component
public class AuthoritativeCampaignResolver {
    public record Resolution(String url, String platform, String state, String source, String sourceUrl, String reason, LocalDateTime observedAt) {
        public Map<String, String> metadata() {
            Map<String, String> values = new TreeMap<>();
            values.put("_issuerCampaign.state", state); values.put("_issuerCampaign.source", source);
            values.put("_issuerCampaign.reason", reason); values.put("_issuerCampaign.observedAt", observedAt.toString());
            if (url != null) values.put("_issuerCampaign.url", url);
            if (sourceUrl != null) values.put("_issuerCampaign.sourceUrl", sourceUrl);
            return values;
        }
    }
    private final IssuerWebsiteClient client;
    public AuthoritativeCampaignResolver(IssuerWebsiteClient client) { this.client = client; }

    public Resolution resolve(Offering offering, Map<String, String> facts) {
        var identity = IntermediaryRegistry.fromFacts(facts);
        if (identity.state() == IntermediaryRegistry.State.AMBIGUOUS || "AMBIGUOUS".equals(facts.get("_secCampaign.state"))) return result(null, "AMBIGUOUS", "SEC_FILED_FACT", offering.secFilingUrl(), "Conflicting filed intermediary/campaign evidence");
        if ("CONFIRMED".equals(facts.get("_secCampaign.state"))) return link(facts.get("_secCampaign.url"), identity.family(), "SEC_FILED_FACT", facts.get("_secCampaign.sourceUrl"));
        if (offering.offeringUrl() != null) {
            if ("CONFIRMED".equals(facts.get("_issuerCampaign.state")) && offering.offeringUrl().equals(facts.get("_issuerCampaign.url"))) {
                Resolution retained = link(offering.offeringUrl(), identity.family(), facts.getOrDefault("_issuerCampaign.source", "ISSUER_WEBSITE_CLAIM"), facts.get("_issuerCampaign.sourceUrl"));
                if ("CONFIRMED".equals(retained.state())) {
                    try { return new Resolution(retained.url(), retained.platform(), retained.state(), retained.source(), retained.sourceUrl(), facts.getOrDefault("_issuerCampaign.reason", retained.reason()), LocalDateTime.parse(facts.get("_issuerCampaign.observedAt"))); }
                    catch (RuntimeException ignored) { return retained; }
                }
                return retained;
            }
            return result(null, "UNRESOLVED", "NONE", null, "Existing campaign resolution is retained separately");
        }
        String website = IntermediaryRegistry.value(facts, "issuerWebsite", "ISSUERWEBSITE");
        if (offering.matchStatus() != MatchStatus.CONFIRMED || website == null) return result(null, "UNRESOLVED", "NONE", null, "Official issuer website and independent issuer match required");
        try {
            String checked = facts.get("_issuerCampaign.observedAt");
            if (checked != null && LocalDateTime.parse(checked).isAfter(LocalDateTime.now().minusDays(1))) return result(null, "UNRESOLVED", "ISSUER_WEBSITE_CLAIM", website, "Issuer homepage inspection cached for 24 hours");
            Set<String> links = ExplicitCampaignLinks.outboundAnchors(client.homepage(website));
            if (links.size() > 1) return result(null, "AMBIGUOUS", "ISSUER_WEBSITE_CLAIM", website, "Multiple explicit campaign links require review");
            if (links.isEmpty()) return result(null, "UNRESOLVED", "ISSUER_WEBSITE_CLAIM", website, "No explicit campaign link on the issuer homepage; no paths guessed");
            return link(links.iterator().next(), identity.family(), "ISSUER_WEBSITE_CLAIM", website);
        } catch (RuntimeException error) { return result(null, "UNAVAILABLE", "ISSUER_WEBSITE_CLAIM", website, "Public issuer homepage unavailable; no bypass attempted"); }
    }
    private static Resolution link(String url, String filedFamily, String source, String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) return result(null, "UNRESOLVED", source, null, "Explicit campaign source reference is required");
        String canonical = url == null ? null : ExplicitCampaignLinks.canonical(url);
        String family = IntermediaryRegistry.familyForUrl(url);
        if (canonical == null) return result(null, "UNRESOLVED", source, sourceUrl, "Explicit URL is not a canonical supported campaign path");
        if (filedFamily != null && !filedFamily.equals(family)) return result(null, "AMBIGUOUS", source, sourceUrl, "Explicit campaign domain conflicts with the filed intermediary");
        return result(canonical, "CONFIRMED", source, sourceUrl, "Exact campaign link observed in authoritative issuer evidence; page fetch remains separate");
    }
    private static Resolution result(String url, String state, String source, String sourceUrl, String reason) {
        return new Resolution(url, url == null ? null : IntermediaryRegistry.familyForUrl(url), state, source, sourceUrl, reason, LocalDateTime.now());
    }
}
