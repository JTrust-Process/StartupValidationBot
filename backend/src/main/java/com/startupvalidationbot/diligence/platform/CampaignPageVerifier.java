package com.startupvalidationbot.diligence.platform;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import com.startupvalidationbot.diligence.CampaignVerification;
import com.startupvalidationbot.diligence.CampaignVerification.Observation;
import com.startupvalidationbot.diligence.CampaignVerification.PageState;
import com.startupvalidationbot.diligence.CampaignVerification.UrlEvidence;
import com.startupvalidationbot.diligence.DiligenceDomain.PlatformCampaign;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.*;
import com.startupvalidationbot.diligence.discovery.CampaignIdentityVerifier;
import com.startupvalidationbot.offering.IntermediaryRegistry;
import com.startupvalidationbot.offering.OfferingDomain.Offering;
import com.startupvalidationbot.radar.CompanyIdentity;

public final class CampaignPageVerifier {
    private CampaignPageVerifier() { }

    public static Observation assess(Offering offering, Map<String, String> facts, UrlEvidence link,
            PlatformCampaign page, int requests) {
        LocalDateTime now = page.lastCheckedAt();
        Map<String, String> content = page.facts();
        String wall = content.get("_page.wall");
        if (wall != null) return observation(offering, link, now, PageState.ACCESS_BLOCKED, "UNKNOWN", wall,
                "", "", "Public page is a login wall or browser challenge; no bypass attempted.", requests);
        if (!"V1.2D".equals(content.get("_campaignParserVersion")) || !"CAMPAIGN".equals(content.get("_page.kind")))
            return observation(offering, link, now, PageState.UNAVAILABLE, "UNKNOWN", "NOT_A_CAMPAIGN",
                    "", "", "HTTP success did not establish a specific public campaign page.", requests);
        String legalIssuer = content.get("_page.legalIssuer");
        if (legalIssuer != null && !CompanyIdentity.normalizeName(legalIssuer).equals(CompanyIdentity.normalizeName(offering.issuerName())))
            return observation(offering, link, now, PageState.ISSUER_MISMATCH, "UNKNOWN", "HTTP_200",
                    "Page legal issuer: " + legalIssuer + "; expected: " + offering.issuerName(), "", "Explicit page legal issuer differs from the retained SEC issuer.", requests);
        if (List.of("AMBIGUOUS", "UNMATCHED", "REJECTED").contains(offering.matchStatus().name()))
            return observation(offering, link, now, PageState.ISSUER_UNRESOLVED, "UNKNOWN", "HTTP_200",
                    "Offering identity: " + offering.matchStatus(), "", "Ambiguous offering identity requires review; page content cannot silently resolve it.", requests);
        if (page.issuerName() == null || page.issuerName().isBlank())
            return observation(offering, link, now, PageState.ISSUER_UNRESOLVED, "UNKNOWN", "HTTP_200",
                    "Expected issuer: " + offering.issuerName(), "", "No reliable issuer name on the accessible page.", requests);
        String website = IntermediaryRegistry.value(facts, "issuerWebsite", "ISSUERWEBSITE");
        var intermediary = IntermediaryRegistry.fromFacts(facts);
        boolean filedPlatform = intermediary.state() == IntermediaryRegistry.State.SEC_CONFIRMED;
        CampaignIdentity expected = new CampaignIdentity(offering.radarCompanyId(), offering.id(), offering.issuerName(),
                website, CompanyIdentity.normalizeDomain(website), List.of(), offering.issuerName(), offering.issuerCik(),
                website, offering.intermediaryName(), offering.secFilingUrl(), intermediary.family() == null ? offering.platform() : intermediary.family(), filedPlatform, "");
        CampaignCandidate candidate = new CampaignCandidate(page.platform(), page.campaignUrl(), page.issuerName(),
                content.get("_page.issuerDomain"), page.status().name(), "", "PUBLIC_CAMPAIGN_URL", 95, Map.of());
        IdentityDecision identity = new CampaignIdentityVerifier().verify(expected, candidate);
        PageState state = identity.status() == IdentityStatus.CONFIRMED ? PageState.VERIFIED
                : identity.status() == IdentityStatus.REJECTED ? PageState.ISSUER_MISMATCH : PageState.ISSUER_UNRESOLVED;
        String evidence = "Expected issuer: " + offering.issuerName() + "; observed issuer: " + page.issuerName()
                + "; observed domain: " + content.getOrDefault("_page.issuerDomain", "not established") + "; " + identity.reason();
        return observation(offering, link, now, state, state == PageState.VERIFIED ? content.getOrDefault("_page.status", "UNKNOWN") : "UNKNOWN",
                "HTTP_200", evidence, state == PageState.VERIFIED ? content.getOrDefault("_page.statusExcerpt", "") : "",
                state == PageState.VERIFIED ? content.getOrDefault("_page.statusExplanation", "Public status not established.") : identity.reason(), requests);
    }

    public static Observation failure(Offering offering, UrlEvidence link, String outcome, String explanation, int requests) {
        PageState state = switch (outcome) {
            case "HTTP_403", "HTTP_429", "BOT_CHALLENGE", "LOGIN_WALL", "ROBOTS_DENIED" -> PageState.ACCESS_BLOCKED;
            case "HTTP_404", "HTTP_410" -> PageState.MISSING;
            case "NO_CANONICAL_URL", "NO_LINEAGE", "UNSUPPORTED_PLATFORM" -> PageState.NOT_CHECKED;
            case "AMBIGUOUS_URL" -> PageState.AMBIGUOUS;
            default -> PageState.UNAVAILABLE;
        };
        if (outcome.startsWith("ROBOTS") || outcome.startsWith("HTTP_429")) state = PageState.ACCESS_BLOCKED;
        return observation(offering, link, LocalDateTime.now(), state, "UNKNOWN", outcome, "", "", explanation, requests);
    }
    private static Observation observation(Offering offering, UrlEvidence link, LocalDateTime observed,
            PageState state, String status, String outcome, String issuer, String excerpt, String explanation, int requests) {
        return new Observation(offering.id(), link.url(), "PLATFORM_ISSUER_CLAIM", link.url(), observed, state,
                status, outcome, issuer, excerpt, "PUBLIC_HTML_EXACT_ISSUER_WITH_SEC_INTERMEDIARY_OR_OFFICIAL_DOMAIN",
                explanation, requests);
    }
}
