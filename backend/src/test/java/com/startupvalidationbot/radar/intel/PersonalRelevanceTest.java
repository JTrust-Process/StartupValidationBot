package com.startupvalidationbot.radar.intel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.startupvalidationbot.radar.intel.InteractionSignal.Summary;

class PersonalRelevanceTest {
    private static final InterestProfile PROFILE = InterestProfile.defaultProfile();
    private static final String TRADING = "Unified terminal for global equities and perpetuals with "
            + "automated market-making strategies.";

    @Test
    void scoresACompanyInsideTheUserInterestsHighly() {
        var result = PersonalRelevance.score("Arbital", TRADING, "Fintech",
                List.of("Fintech", "Trading", "Crypto"), PROFILE, Summary.empty());

        assertThat(result.score()).isGreaterThanOrEqualTo(65);
        assertThat(result.matchedInterests()).contains("Fintech", "Trading infrastructure");
        assertThat(result.reasons()).anyMatch(reason -> reason.startsWith("Matches your interests"));
    }

    @Test
    void scoresAnUnrelatedCompanyLow() {
        var result = PersonalRelevance.score("Green Acres",
                "Regenerative agriculture cooperative for smallholder farms.", "Agriculture",
                List.of("Agriculture"), PROFILE, Summary.empty());

        assertThat(result.score()).isLessThanOrEqualTo(35);
        assertThat(result.matchedInterests()).isEmpty();
    }

    @Test
    void engagementSignalsRaiseRelevanceAndAreExplained() {
        var baseline = PersonalRelevance.score("Arbital", TRADING, "Fintech", List.of("Fintech"), PROFILE,
                Summary.empty());
        var engaged = PersonalRelevance.score("Arbital", TRADING, "Fintech", List.of("Fintech"), PROFILE,
                new Summary(1, 0, 1, 1));

        assertThat(engaged.score()).isGreaterThan(baseline.score());
        assertThat(engaged.reasons()).anyMatch(reason -> reason.contains("watchlist"));
        assertThat(engaged.reasons()).anyMatch(reason -> reason.contains("Deep Dive"));
    }

    @Test
    void ignoringACompanyHoldsItsRelevanceDown() {
        var result = PersonalRelevance.score("Arbital", TRADING, "Fintech", List.of("Fintech", "Trading"),
                PROFILE, new Summary(0, 1, 0, 0));

        assertThat(result.score()).isLessThanOrEqualTo(12);
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("ignored"));
    }

    @Test
    void neverExceedsOneHundred() {
        var result = PersonalRelevance.score("Everything",
                "ai automation fintech trading developer security enterprise robotics quantum marketplace",
                "AI", List.of("AI", "Fintech", "Trading", "Developer Tools", "Cybersecurity", "Robotics"),
                PROFILE, new Summary(3, 0, 3, 3));

        assertThat(result.score()).isBetween(0, 100);
    }

    /**
     * Interest keywords are matched as whole words. Substring matching would score unrelated companies:
     * "erp" occurs inside "perpetuals", and "ai" inside "retail", "email", "maintain" and "chain".
     */
    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "Trading terminal for perpetuals.;Enterprise software",
            "Retail email marketing for small shops.;AI",
            "We maintain legacy mainframes.;AI",
            "Supply chain visibility for grocers.;AI"
    })
    void doesNotMatchInterestKeywordsAsSubstrings(String description, String forbiddenInterest) {
        var result = PersonalRelevance.score("Test", description, "Other", List.of(), PROFILE, Summary.empty());
        assertThat(result.matchedInterests()).doesNotContain(forbiddenInterest);
    }

    @Test
    void stillMatchesGenuineInterestMentions() {
        var result = PersonalRelevance.score("Test", "Agentic AI inference platform for developers.", "AI",
                List.of("AI", "Developer Tools"), PROFILE, Summary.empty());

        assertThat(result.matchedInterests()).contains("AI", "Developer tools");
    }

    @Test
    void fallsBackToTheDefaultProfileWhenNoneIsConfigured() {
        var result = PersonalRelevance.score("Arbital", TRADING, "Fintech", List.of("Trading"),
                new InterestProfile(List.of()), Summary.empty());

        assertThat(result.matchedInterests()).isNotEmpty();
    }
}
