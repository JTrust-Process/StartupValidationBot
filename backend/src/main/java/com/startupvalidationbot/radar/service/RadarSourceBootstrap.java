package com.startupvalidationbot.radar.service;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.startupvalidationbot.radar.ContentHash;
import com.startupvalidationbot.radar.RadarStore;

@Component
public class RadarSourceBootstrap {
    private final RadarStore store;
    private final String productHuntToken;
    private final String rssUrls;
    private final boolean hackerNewsEnabled;

    public RadarSourceBootstrap(RadarStore store,
            @Value("${radar.product-hunt-token:}") String productHuntToken,
            @Value("${radar.rss-urls:}") String rssUrls,
            @Value("${radar.enable-hacker-news:true}") boolean hackerNewsEnabled) {
        this.store = store;
        this.productHuntToken = productHuntToken;
        this.rssUrls = rssUrls;
        this.hackerNewsEnabled = hackerNewsEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void configureSources() {
        store.upsertSource("manual", "MANUAL", "Manual startup discovery", null, true);
        store.upsertSource("product-hunt", "PRODUCT_HUNT", "Product Hunt",
                "https://api.producthunt.com/v2/api/graphql", !productHuntToken.isBlank());

        // Official public HN Search API. No key, no anti-bot controls, and Launch HN posts are founder
        // announcements rather than press coverage, so the signal-to-noise ratio is unusually good.
        store.upsertSource("hacker-news-launch", "HACKER_NEWS", "Hacker News launches",
                "https://hn.algolia.com/api/v1/search_by_date", hackerNewsEnabled);

        // Curated feeds are registered disabled. Each is an official publisher RSS feed; enable the
        // ones you actually want rather than ingesting everything.
        registerPreset("rss-preset-techcrunch-venture", "TechCrunch venture (preset)",
                "https://techcrunch.com/category/venture/feed/");
        registerPreset("rss-preset-eu-startups", "EU-Startups (preset)",
                "https://www.eu-startups.com/feed/");
        registerPreset("rss-preset-a16z", "Andreessen Horowitz (preset)", "https://a16z.com/feed/");
        Arrays.stream(rssUrls.split("[;,]"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .forEach(url -> store.upsertSource("rss-" + ContentHash.sha256(url).substring(0, 12),
                        "RSS", "Configured startup feed", url, true));
    }

    /**
     * Registers a preset feed without enabling it and without resetting a choice already made: if the
     * source row exists, its enabled flag is left exactly as the user set it.
     */
    private void registerPreset(String key, String name, String url) {
        boolean enabled = store.findSource(key).map(existing -> existing.enabled()).orElse(false);
        store.upsertSource(key, "RSS", name, url, enabled);
    }
}
