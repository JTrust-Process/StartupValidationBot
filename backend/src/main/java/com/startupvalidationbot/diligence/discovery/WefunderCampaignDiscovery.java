package com.startupvalidationbot.diligence.discovery;

import java.util.List;

import org.springframework.stereotype.Component;

import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.CampaignIdentity;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.DiscoveryAttempt;

@Component
public class WefunderCampaignDiscovery implements PlatformCampaignDiscovery {
    @Override public String platform() { return "WEFUNDER"; }
    @Override public String capability() { return "EXPLICIT_LINK_REQUIRED"; }

    @Override
    public DiscoveryAttempt discover(CampaignIdentity identity, int maxCandidates, CampaignDiscoveryContext context) {
        // SEC and issuer links use the authoritative resolver; names cannot establish URL lineage.
        return DiscoveryAttempt.success(List.of(), capability());
    }
}
