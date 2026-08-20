package com.startupvalidationbot.radar.intel;

/**
 * The persisted inputs personal relevance needs: the user's interest profile and their interaction
 * history with a company.
 *
 * Introducing this seam keeps {@link PersonalRelevance} scoring usable without a database, so the
 * scoring service stays unit-testable and any future non-persistent caller gets sane defaults rather
 * than a null store.
 */
public interface PersonalRelevanceInputs {

    InterestProfile loadProfile();

    InteractionSignal.Summary signalSummary(long companyId);

    /** Default profile, no interaction history. Used by unit tests and any path without persistence. */
    static PersonalRelevanceInputs defaults() {
        return new PersonalRelevanceInputs() {
            @Override
            public InterestProfile loadProfile() {
                return InterestProfile.defaultProfile();
            }

            @Override
            public InteractionSignal.Summary signalSummary(long companyId) {
                return InteractionSignal.Summary.empty();
            }
        };
    }
}
