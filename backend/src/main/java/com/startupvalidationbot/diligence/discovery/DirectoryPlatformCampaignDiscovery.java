package com.startupvalidationbot.diligence.discovery;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.CampaignCandidate;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.CampaignIdentity;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.DiscoveryAttempt;
import com.startupvalidationbot.diligence.platform.PlatformUrlPolicy;

abstract class DirectoryPlatformCampaignDiscovery implements PlatformCampaignDiscovery {
    private final URI directory;

    DirectoryPlatformCampaignDiscovery(String directory) {
        this.directory = URI.create(directory);
    }

    @Override
    public DiscoveryAttempt discover(CampaignIdentity identity, int maxCandidates, CampaignDiscoveryContext context) {
        try {
            List<CampaignCandidate> listing = parse(identity, context.get(platform(), directory), maxCandidates);
            List<CampaignCandidate> enriched = new ArrayList<>();
            for (CampaignCandidate candidate : listing) {
                CampaignCandidate value = candidate;
                try {
                    String page = context.get(platform(), URI.create(candidate.campaignUrl()));
                    String pageName = CampaignHtml.pageName(page);
                    String domain = CampaignHtml.issuerDomain(platform(), page);
                    value = new CampaignCandidate(candidate.platform(), candidate.campaignUrl(),
                            pageName == null ? candidate.issuerName() : pageName, domain,
                            CampaignHtml.status(page), candidate.platformIdentifier(), candidate.campaignUrl(),
                            domain == null ? 75 : 85, candidate.metadata());
                } catch (RuntimeException ignored) {
                    // A public listing match remains reviewable when its detail page is unavailable.
                }
                enriched.add(value);
            }
            return DiscoveryAttempt.success(enriched, capability());
        } catch (RuntimeException error) {
            return DiscoveryAttempt.failed(capability(), safe(error));
        }
    }

    List<CampaignCandidate> parse(CampaignIdentity identity, String html, int maxCandidates) {
        Map<String, CampaignCandidate> matches = new LinkedHashMap<>();
        for (CampaignHtml.Link link : CampaignHtml.links(html)) {
            if (!campaignPath(link.href()) || !CampaignHtml.plausibleName(link.name(), identity)) continue;
            URI resolved = CampaignHtml.absolute(directory, link.href());
            if (resolved == null) continue;
            try { resolved = PlatformUrlPolicy.require(platform(), resolved.toString()); }
            catch (IllegalArgumentException ignored) { continue; }
            String url = canonical(resolved);
            if (url.length() > 1200) continue;
            matches.putIfAbsent(url, new CampaignCandidate(platform(), url, link.name(), null,
                    CampaignHtml.status(link.text()), identifier(resolved), directory.toString(), 70,
                    Map.of("listingName", bounded(link.name(), 300))));
            if (matches.size() >= Math.max(1, maxCandidates)) break;
        }
        return List.copyOf(matches.values());
    }

    protected abstract boolean campaignPath(String href);

    protected String canonical(URI uri) { return uri.toString(); }

    private static String identifier(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank()) return "";
        String[] parts = path.split("/");
        return parts.length == 0 ? "" : parts[parts.length - 1];
    }

    private static String bounded(String value, int max) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), max));
    }

    private static String safe(RuntimeException error) {
        String value = error.getMessage();
        return value == null ? error.getClass().getSimpleName() : value;
    }
}
