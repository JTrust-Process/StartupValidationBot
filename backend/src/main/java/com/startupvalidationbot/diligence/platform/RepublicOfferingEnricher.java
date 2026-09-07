package com.startupvalidationbot.diligence.platform;

import org.springframework.stereotype.Component;

@Component
public class RepublicOfferingEnricher extends AbstractPlatformOfferingEnricher {
    public RepublicOfferingEnricher(PlatformHttpClient client) { super(client); }
    @Override public String platform() { return "REPUBLIC"; }
}
