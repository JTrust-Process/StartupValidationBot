package com.startupvalidationbot.diligence.discovery;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import com.startupvalidationbot.diligence.platform.PlatformHttpClient;

public final class CampaignDiscoveryContext {
    private final PlatformHttpClient client;
    private final int maxRequestsPerPlatform;
    private final Map<String, String> pages = new HashMap<>();
    private final Map<String, String> failures = new HashMap<>();
    private final Map<String, Integer> requests = new HashMap<>();

    public CampaignDiscoveryContext(PlatformHttpClient client, int maxRequestsPerPlatform) {
        this.client = client;
        this.maxRequestsPerPlatform = Math.max(1, Math.min(maxRequestsPerPlatform, 25));
    }

    public String get(String platform, URI uri) {
        String key = platform + "|" + uri;
        String cached = pages.get(key);
        if (cached != null) return cached;
        String failed = failures.get(key);
        if (failed != null) throw new IllegalStateException(failed);
        if (requests(platform) >= maxRequestsPerPlatform) {
            throw new IllegalStateException("Campaign discovery request budget exhausted for " + platform);
        }
        requests.merge(platform, 1, Integer::sum);
        try {
            String page = client.get(platform, uri);
            pages.put(key, page);
            return page;
        } catch (RuntimeException error) {
            failures.put(key, error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
            throw error;
        }
    }

    public int requests(String platform) {
        return requests.getOrDefault(platform, 0);
    }
}
