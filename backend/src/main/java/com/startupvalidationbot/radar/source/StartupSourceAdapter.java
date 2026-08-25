package com.startupvalidationbot.radar.source;

import java.util.List;

import com.startupvalidationbot.radar.RadarDomain.Candidate;
import com.startupvalidationbot.radar.RadarDomain.Source;

public interface StartupSourceAdapter {
    boolean supports(String sourceType);

    List<Candidate> discover(Source source, int limit) throws SourceFetchException;
}
