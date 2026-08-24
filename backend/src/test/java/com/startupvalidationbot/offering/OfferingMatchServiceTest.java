package com.startupvalidationbot.offering;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.startupvalidationbot.offering.OfferingDomain.Candidate;
import com.startupvalidationbot.offering.OfferingDomain.MatchStatus;
import com.startupvalidationbot.radar.RadarDomain.Company;

class OfferingMatchServiceTest {
    private final OfferingMatchService matcher = new OfferingMatchService();

    @Test
    void confirmsUniqueLegalNameAndDomainButRejectsAConflictingDomain() {
        Company company = company(7, "Acme Technologies", "acme.example", List.of("Acme Technologies, Inc."));
        assertThat(matcher.match(candidate("Acme Technologies, Inc.", "https://acme.example"), List.of(company)).status())
                .isEqualTo(MatchStatus.CONFIRMED);
        assertThat(matcher.match(candidate("Acme Technologies, Inc.", "https://other.example"), List.of(company)).status())
                .isEqualTo(MatchStatus.REJECTED);
    }

    @Test
    void neverConfirmsFuzzyOnlyOrAmbiguousNames() {
        Company acme = company(1, "Acme", "acme.example", List.of());
        assertThat(matcher.match(candidate("Acme Labs", null), List.of(acme)).status()).isEqualTo(MatchStatus.LIKELY);
        Company duplicate = company(2, "Acme", "other.example", List.of());
        assertThat(matcher.match(candidate("Acme", null), List.of(acme, duplicate)).status())
                .isEqualTo(MatchStatus.AMBIGUOUS);
    }

    private static Candidate candidate(String name, String website) {
        return new Candidate(name, "0001234567", website, "Wefunder", "Wefunder Portal LLC", null, null,
                "https://www.sec.gov/Archives/example", "0001234567-26-000001", "020-12345", "C",
                java.time.LocalDate.now(), "Crowd SAFE", null, null, null, null, null, null,
                "TEST", Map.of());
    }

    private static Company company(long id, String name, String domain, List<String> aliases) {
        return new Company(id, name, domain, "https://" + domain, "", "Unknown", List.of(), null, null,
                aliases, 0, 0, "", 1, LocalDateTime.now(), LocalDateTime.now(), false, false);
    }
}
