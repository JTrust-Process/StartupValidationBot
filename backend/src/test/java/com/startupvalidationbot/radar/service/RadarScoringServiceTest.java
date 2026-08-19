package com.startupvalidationbot.radar.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.startupvalidationbot.radar.RadarDomain.Company;

class RadarScoringServiceTest {
    @Test
    void keepsRadarAndPersonalScoresSeparate() {
        RadarScoringService service = new RadarScoringService("energy,developer tools");
        Company company = new Company(1L, "Grid Lab", "gridlab.test", "https://gridlab.test",
                "Energy infrastructure automation with paying enterprise customers and revenue growth.",
                "Energy", List.of("Infrastructure", "Automation"), null, 2025, List.of(), 0, 0, "", 2,
                LocalDateTime.now(), LocalDateTime.now(), false, false);

        var result = service.score(company);

        assertThat(result.radarScore()).isBetween(0, 100);
        assertThat(result.personalScore()).isBetween(0, 100);
        assertThat(result.whyInteresting()).anyMatch(value -> value.contains("Radar score"));
        assertThat(result.inferences()).anyMatch(value -> value.contains("not verified"));
    }

    /**
     * Configured themes are matched as whole words or phrases. Substring matching would fire on
     * "erp" inside "perpetuals" and "ai" inside "retail", "email", "maintain" and "chain".
     */
    @Test
    void matchesConfiguredInterestsAsWholeWordsOrPhrases() {
        RadarScoringService service = new RadarScoringService("erp,ai");
        Company falsePositive = company("Perpetual Systems",
                "Retail email tools maintain a perpetuals trading chain.");
        Company realMatch = company("Workflow AI", "AI infrastructure for ERP teams.");

        var falsePositiveResult = service.score(falsePositive);
        var realMatchResult = service.score(realMatch);

        assertThat(falsePositiveResult.whyInteresting())
                .noneMatch(value -> value.startsWith("Matches your themes"));
        assertThat(realMatchResult.whyInteresting())
                .anyMatch(value -> value.equals("Matches your themes: erp, ai."));
    }

    /**
     * The same whole-word rule applies to the personal relevance interest profile, which is scored
     * independently of the configured themes above.
     */
    @Test
    void doesNotMatchPersonalInterestsOnSubstrings() {
        RadarScoringService service = new RadarScoringService("energy");
        Company falsePositive = company("Perpetual Systems",
                "Retail email tools maintain a perpetuals chain for shops.");

        var result = service.score(falsePositive);

        assertThat(result.personalScoreInputs())
                .noneMatch(value -> value.contains("Enterprise software"))
                .noneMatch(value -> value.contains("AI"));
        assertThat(result.personalScore()).isBetween(0, 100);
    }

    private static Company company(String name, String description) {
        return new Company(2L, name, null, null, description, "Unknown", List.of(), null, null, List.of(),
                0, 0, "", 1, LocalDateTime.now(), LocalDateTime.now(), false, false);
    }
}
