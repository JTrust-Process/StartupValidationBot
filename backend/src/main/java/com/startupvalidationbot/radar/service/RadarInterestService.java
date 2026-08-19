package com.startupvalidationbot.radar.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.startupvalidationbot.radar.RadarDomain.Company;
import com.startupvalidationbot.radar.RadarIntelStore;
import com.startupvalidationbot.radar.RadarIntelViews.InterestProfileView;
import com.startupvalidationbot.radar.RadarIntelViews.InterestView;
import com.startupvalidationbot.radar.RadarIntelViews.RelevanceExplanation;
import com.startupvalidationbot.radar.RadarStore;
import com.startupvalidationbot.radar.intel.InteractionSignal;
import com.startupvalidationbot.radar.intel.InterestProfile;
import com.startupvalidationbot.radar.intel.InterestProfile.Interest;
import com.startupvalidationbot.radar.intel.PersonalRelevance;

/**
 * Configurable, transparent personal relevance.
 *
 * Editing interests recomputes every personal score immediately. That recompute is deterministic and
 * costs zero AI calls, so the user can tune their profile freely without spending model budget.
 */
@Service
public class RadarInterestService {
    private final RadarIntelStore intelStore;
    private final RadarStore store;

    public RadarInterestService(RadarIntelStore intelStore, RadarStore store) {
        this.intelStore = intelStore;
        this.store = store;
    }

    public InterestProfileView profile() {
        return toView(intelStore.loadProfile(), intelStore.profileUpdatedAt().orElse(null));
    }

    /** Saves the profile and returns how many companies had their personal score refreshed. */
    public SaveResult save(List<InterestView> interests) {
        InterestProfile profile = new InterestProfile(interests == null ? List.of() : interests.stream()
                .map(view -> new Interest(view.label(), view.weight(), view.keywords()))
                .toList());
        InterestProfile saved = intelStore.saveProfile(profile);
        return new SaveResult(toView(saved, LocalDateTime.now()), recomputePersonalScores());
    }

    /** Deterministic refresh of every personal score. No AI, no external calls. */
    public int recomputePersonalScores() {
        InterestProfile profile = intelStore.loadProfile();
        List<Company> companies = store.listCompanies();
        var summaries = intelStore.signalSummaries(companies.stream().map(Company::id).toList());

        int updated = 0;
        for (Company company : companies) {
            var relevance = PersonalRelevance.score(company.name(), company.description(), company.sector(),
                    company.categories(), profile,
                    summaries.getOrDefault(company.id(), InteractionSignal.Summary.empty()));
            if (relevance.score() != company.personalScore()) {
                intelStore.updatePersonalScore(company.id(), relevance.score());
                updated++;
            }
        }
        return updated;
    }

    public RelevanceExplanation explain(long companyId) {
        Company company = store.findCompany(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown company " + companyId));
        var relevance = PersonalRelevance.score(company.name(), company.description(), company.sector(),
                company.categories(), intelStore.loadProfile(), intelStore.signalSummary(companyId));
        return new RelevanceExplanation(relevance.score(), relevance.matchedInterests(), relevance.reasons());
    }

    /** Records an interaction and refreshes only the affected company's personal score. */
    public RelevanceExplanation recordSignal(long companyId, String signalType) {
        InteractionSignal signal = InteractionSignal.parse(signalType);
        store.findCompany(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown company " + companyId));
        intelStore.recordSignal(companyId, signal);
        RelevanceExplanation explanation = explain(companyId);
        intelStore.updatePersonalScore(companyId, explanation.score());
        return explanation;
    }

    private static InterestProfileView toView(InterestProfile profile, LocalDateTime updatedAt) {
        return new InterestProfileView(profile.interests().stream()
                .map(interest -> new InterestView(interest.label(), interest.weight(), interest.keywords()))
                .toList(), updatedAt);
    }

    public record SaveResult(InterestProfileView profile, int companiesRescored) {
    }
}
