package com.startupvalidationbot.radar.source;

import java.util.List;

import org.springframework.stereotype.Component;

import com.startupvalidationbot.radar.RadarDomain.Candidate;
import com.startupvalidationbot.radar.RadarDomain.Source;

@Component
public class ManualSourceAdapter implements StartupSourceAdapter {
    @Override
    public boolean supports(String sourceType) {
        return "MANUAL".equalsIgnoreCase(sourceType) || "YC_DIRECTORY".equalsIgnoreCase(sourceType);
    }

    @Override
    public List<Candidate> discover(Source source, int limit) {
        return List.of();
    }
}
