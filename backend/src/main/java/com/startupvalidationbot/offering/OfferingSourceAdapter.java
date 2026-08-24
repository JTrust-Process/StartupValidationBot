package com.startupvalidationbot.offering;

import java.util.List;

import com.startupvalidationbot.offering.OfferingDomain.Candidate;

public interface OfferingSourceAdapter {
    List<Candidate> fetchRecent();
    List<Candidate> fetchBaseline();
    default Candidate enrich(Candidate candidate) { return candidate; }
}
