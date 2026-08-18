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

    public RadarSourceBootstrap(RadarStore store,
            @Value("${radar.product-hunt-token:}") String productHuntToken,
            @Value("${radar.rss-urls:}") String rssUrls) {
        this.store = store;
        this.productHuntToken = productHuntToken;
        this.rssUrls = rssUrls;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void configureSources() {
        store.upsertSource("manual", "MANUAL", "Manual startup discovery", null, true);
        store.upsertSource("product-hunt", "PRODUCT_HUNT", "Product Hunt",
                "https://api.producthunt.com/v2/api/graphql", !productHuntToken.isBlank());
        Arrays.stream(rssUrls.split("[;,]"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .forEach(url -> store.upsertSource("rss-" + ContentHash.sha256(url).substring(0, 12),
                        "RSS", "Configured startup feed", url, true));
    }
}
