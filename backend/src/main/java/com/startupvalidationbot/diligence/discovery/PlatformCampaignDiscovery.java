package com.startupvalidationbot.diligence.discovery;

import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.CampaignIdentity;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.DiscoveryAttempt;

public interface PlatformCampaignDiscovery {
    String platform();
    String capability();
    DiscoveryAttempt discover(CampaignIdentity identity, int maxCandidates, CampaignDiscoveryContext context);
}
