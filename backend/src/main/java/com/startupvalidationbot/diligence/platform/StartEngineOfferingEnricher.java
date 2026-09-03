package com.startupvalidationbot.diligence.platform;

import org.springframework.stereotype.Component;

@Component
public class StartEngineOfferingEnricher extends AbstractPlatformOfferingEnricher {
    public StartEngineOfferingEnricher(PlatformHttpClient client) { super(client); }
    @Override public String platform() { return "STARTENGINE"; }
}
