package com.startupvalidationbot.diligence.discovery;

import org.springframework.stereotype.Component;

@Component
public class StartEngineCampaignDiscovery extends DirectoryPlatformCampaignDiscovery {
    public StartEngineCampaignDiscovery() { super("https://www.startengine.com/explore"); }

    @Override public String platform() { return "STARTENGINE"; }
    @Override public String capability() { return "PUBLIC_DIRECTORY"; }

    @Override
    protected boolean campaignPath(String href) {
        if (href == null) return false;
        String path = href.replaceFirst("^https?://(?:www\\.)?startengine\\.com", "")
                .replaceAll("[?#].*$", "");
        return path.matches("/offering/[A-Za-z0-9._-]+/?");
    }
}
