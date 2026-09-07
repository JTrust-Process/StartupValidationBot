package com.startupvalidationbot.diligence.discovery;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.CampaignCandidate;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.CampaignIdentity;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.DiscoveryAttempt;
import com.startupvalidationbot.diligence.platform.PlatformUrlPolicy;
import com.startupvalidationbot.radar.CompanyIdentity;

@Component
public class WefunderCampaignDiscovery implements PlatformCampaignDiscovery {
    @Override public String platform() { return "WEFUNDER"; }
    @Override public String capability() { return "TARGETED_CANONICAL_PROBE"; }

    @Override
    public DiscoveryAttempt discover(CampaignIdentity identity, int maxCandidates, CampaignDiscoveryContext context) {
        List<CampaignCandidate> candidates = new ArrayList<>();
        String failure = null;
        for (String slug : slugs(identity, maxCandidates)) {
            URI uri = PlatformUrlPolicy.require(platform(), "https://wefunder.com/" + slug);
            try {
                String html = context.get(platform(), uri);
                String issuer = CampaignHtml.pageName(html);
                if (issuer == null || !CampaignHtml.plausibleName(issuer, identity)) continue;
                candidates.add(new CampaignCandidate(platform(), uri.toString(), issuer,
                        CampaignHtml.issuerDomain(platform(), html), CampaignHtml.status(html), slug,
                        "TARGETED_CANONICAL_PROBE", 75,
                        Map.of("pageTitle", bounded(CampaignHtml.pageName(html), 300))));
                if (candidates.size() >= Math.max(1, maxCandidates)) break;
            } catch (RuntimeException error) {
                String message = safe(error);
                if (message.contains("HTTP 404")) continue;
                failure = message;
                break;
            }
        }
        if (!candidates.isEmpty()) return DiscoveryAttempt.success(candidates, capability());
        return failure == null ? DiscoveryAttempt.success(List.of(), capability())
                : DiscoveryAttempt.failed(capability(), failure);
    }

    List<String> slugs(CampaignIdentity identity, int maxCandidates) {
        Set<String> values = new LinkedHashSet<>();
        add(values, identity.companyName());
        add(values, identity.issuerName());
        identity.aliases().forEach(value -> add(values, value));
        return values.stream().limit(Math.max(1, maxCandidates)).toList();
    }

    private static void add(Set<String> target, String value) {
        String normalized = CompanyIdentity.normalizeName(value).toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) return;
        target.add(normalized.replace(" ", ""));
        if (normalized.contains(" ")) target.add(normalized.replace(' ', '-'));
        String withoutSuffix = normalized.replaceFirst("\\s+(?:inc|incorporated|llc|ltd|corp|corporation)$", "");
        if (!withoutSuffix.equals(normalized)) {
            target.add(withoutSuffix.replace(" ", ""));
            if (withoutSuffix.contains(" ")) target.add(withoutSuffix.replace(' ', '-'));
        }
    }

    private static String bounded(String value, int max) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), max));
    }

    private static String safe(RuntimeException error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
