package com.startupvalidationbot.radar.intel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.startupvalidationbot.radar.intel.SimilarityScorer.CompanyFacets;

class SimilarityScorerTest {
    private static final CompanyFacets TARGET = new CompanyFacets(1, "Arbital", "fintech",
            List.of("Fintech", "Trading", "Crypto"), List.of("trading-infra"), "marketplace");

    private static final List<CompanyFacets> CANDIDATES = List.of(
            new CompanyFacets(2, "Helio Markets", "fintech", List.of("Fintech", "Trading", "Crypto"),
                    List.of("trading-infra"), "marketplace"),
            new CompanyFacets(3, "Ledgerly", "fintech", List.of("Fintech", "Payments"),
                    List.of("trading-infra"), "saas"),
            new CompanyFacets(4, "AgriSense", "agriculture", List.of("Agriculture"), List.of(), "hardware"));

    @Test
    void ranksTheClosestCompanyFirstAndExplainsWhy() {
        var similar = SimilarityScorer.rank(TARGET, CANDIDATES, 5);

        assertThat(similar).isNotEmpty();
        assertThat(similar.get(0).companyId()).isEqualTo(2);
        assertThat(similar.get(0).relationship()).isEqualTo("Likely competitor");
        assertThat(similar).allMatch(company -> !company.reasons().isEmpty());
    }

    @Test
    void excludesUnrelatedCompaniesAndTheTargetItself() {
        var similar = SimilarityScorer.rank(TARGET, CANDIDATES, 5);

        assertThat(similar).extracting(SimilarityScorer.SimilarCompany::companyId)
                .doesNotContain(1L)
                .doesNotContain(4L);
    }

    @Test
    void honoursTheResultLimit() {
        assertThat(SimilarityScorer.rank(TARGET, CANDIDATES, 1)).hasSize(1);
    }

    @Test
    void handlesEmptyInputSafely() {
        assertThat(SimilarityScorer.rank(TARGET, List.of(), 5)).isEmpty();
        assertThat(SimilarityScorer.rank(null, CANDIDATES, 5)).isEmpty();
    }

    @Test
    void onlyClaimsCompetitorOnHeavyOverlap() {
        assertThat(SimilarityScorer.relationship(0.75, 3, true, true)).isEqualTo("Likely competitor");
        assertThat(SimilarityScorer.relationship(0.40, 1, true, true)).isEqualTo("Adjacent company");
        assertThat(SimilarityScorer.relationship(0.10, 1, true, false)).isEqualTo("Same emerging trend");
    }
}
