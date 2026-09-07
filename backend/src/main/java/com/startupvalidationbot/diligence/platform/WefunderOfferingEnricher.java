package com.startupvalidationbot.diligence.platform;

import org.springframework.stereotype.Component;

@Component
public class WefunderOfferingEnricher extends AbstractPlatformOfferingEnricher {
    public WefunderOfferingEnricher(PlatformHttpClient client) { super(client); }
    @Override public String platform() { return "WEFUNDER"; }
}
