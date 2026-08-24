package com.startupvalidationbot.offering;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.startupvalidationbot.offering.OfferingDomain.Candidate;
import com.startupvalidationbot.offering.OfferingDomain.DiscoveryResult;
import com.startupvalidationbot.offering.OfferingDomain.Match;
import com.startupvalidationbot.offering.OfferingDomain.MatchStatus;
import com.startupvalidationbot.radar.RadarDomain.Company;
import com.startupvalidationbot.radar.RadarStore;

@Service
public class OfferingDiscoveryService {
    private final OfferingSourceAdapter source;
    private final OfferingMatchService matcher;
    private final OfferingStore store;
    private final RadarStore radarStore;
    private final int baselineRefreshDays;

    public OfferingDiscoveryService(OfferingSourceAdapter source, OfferingMatchService matcher,
            OfferingStore store, RadarStore radarStore,
            @Value("${offering.sec.baseline-refresh-days:30}") int baselineRefreshDays) {
        this.source = source;
        this.matcher = matcher;
        this.store = store;
        this.radarStore = radarStore;
        this.baselineRefreshDays = Math.max(7, Math.min(baselineRefreshDays, 90));
    }

    public DiscoveryResult discover() {
        List<String> errors = new ArrayList<>();
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        fetch("sec-form-c-recent", source::fetchRecent, candidates, errors);
        if (store.baselineDue(baselineRefreshDays)) {
            fetch("sec-cf-baseline", source::fetchBaseline, candidates, errors);
        }

        List<Company> companies = radarStore.listCompanies();
        int created = 0;
        int updated = 0;
        int confirmed = 0;
        int possible = 0;
        for (Candidate initialCandidate : candidates.values()) {
            try {
                java.util.Optional<Long> establishedCompany = store.findCompanyByCik(initialCandidate.issuerCik());
                Match match = establishedCompany
                        .map(id -> new Match(id, MatchStatus.CONFIRMED, 100,
                                "Issuer CIK matches a previously confirmed offering identity."))
                        .orElseGet(() -> matcher.match(initialCandidate, companies));
                if (match.status() == MatchStatus.UNMATCHED || match.status() == MatchStatus.REJECTED) continue;
                Candidate candidate = source.enrich(initialCandidate);
                if (establishedCompany.isEmpty()) {
                    Match enrichedMatch = matcher.match(candidate, companies);
                    if (enrichedMatch.status() == MatchStatus.REJECTED || enrichedMatch.status() == MatchStatus.UNMATCHED) continue;
                    match = enrichedMatch;
                }
                OfferingStore.UpsertResult result = store.upsert(candidate, match);
                if (result.created()) created++; else updated++;
                if (match.status() == MatchStatus.CONFIRMED) confirmed++; else possible++;
            } catch (RuntimeException error) {
                errors.add("Offering " + initialCandidate.accessionNumber() + " failed: " + safe(error));
            }
        }
        return new DiscoveryResult(candidates.size(), created, updated, confirmed, possible,
                errors.size(), List.copyOf(errors));
    }

    private void fetch(String sourceKey, CandidateSupplier supplier, Map<String, Candidate> target,
            List<String> errors) {
        try {
            List<Candidate> fetched = supplier.get();
            fetched.forEach(candidate -> target.put(candidate.accessionNumber(), candidate));
            store.markSource(sourceKey, "OK", null, fetched.size());
        } catch (RuntimeException error) {
            String message = safe(error);
            store.markSource(sourceKey, "ERROR", message, 0);
            errors.add(sourceKey + ": " + message);
        }
    }

    private static String safe(RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName()
                : message.replaceAll("https?://\\S+", "[external URL]");
    }

    @FunctionalInterface
    private interface CandidateSupplier { List<Candidate> get(); }
}
