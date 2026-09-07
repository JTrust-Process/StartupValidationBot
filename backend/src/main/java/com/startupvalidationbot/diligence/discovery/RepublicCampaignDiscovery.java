package com.startupvalidationbot.diligence.discovery;

import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class RepublicCampaignDiscovery extends DirectoryPlatformCampaignDiscovery {
    private static final Set<String> EXCLUDED = Set.of("", "companies", "investment-opportunities", "login",
            "signup", "help", "about", "insights", "events", "raise", "investors", "portfolio", "trading");

    public RepublicCampaignDiscovery() { super("https://republic.com/companies"); }

    @Override public String platform() { return "REPUBLIC"; }
    @Override public String capability() { return "PUBLIC_DIRECTORY"; }

    @Override
    protected boolean campaignPath(String href) {
        String path = href == null ? "" : href.replaceFirst("^https?://(?:www\\.)?republic\\.com", "")
                .replaceAll("[?#].*$", "").replaceAll("^/+|/+$", "");
        return !path.contains("/") && !EXCLUDED.contains(path.toLowerCase());
    }
}
