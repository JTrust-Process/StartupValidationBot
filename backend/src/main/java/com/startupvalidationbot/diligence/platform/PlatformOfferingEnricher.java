package com.startupvalidationbot.diligence.platform;

import java.net.URI;

import com.startupvalidationbot.diligence.DiligenceDomain.PlatformCampaign;
import com.startupvalidationbot.offering.OfferingDomain.Offering;

public interface PlatformOfferingEnricher {
    String platform();
    boolean supports(URI uri);
    PlatformCampaign enrich(Offering offering, URI campaignUrl);
}
