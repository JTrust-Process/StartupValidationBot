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
    void confirmsOnlyWhenExactNameAndIssuerDomainMatch() {
        Company company = company(7, "Acme Technologies", "acme.example", List.of("Acme Technologies, Inc."));
        var match = matcher.match(candidate("Acme Technologies, Inc.", "https://acme.example"), List.of(company));
        assertThat(match.status()).isEqualTo(MatchStatus.CONFIRMED);
        assertThat(match.confidence()).isEqualTo(100);
        assertThat(match.reason()).contains("issuer website domain match");
    }

    @Test
    void rejectsAnExactNameWithAConflictingDomain() {
        Company company = company(7, "Acme Technologies", "acme.example", List.of());
        assertThat(matcher.match(candidate("Acme Technologies", "https://other.example"), List.of(company)).status())
                .isEqualTo(MatchStatus.REJECTED);
    }

    @Test
    void keepsUniqueExactNameWithoutDomainAsLikely() {
        Company company = company(7, "Acme Technologies", "acme.example", List.of());
        var match = matcher.match(candidate("Acme Technologies", null), List.of(company));
        assertThat(match.status()).isEqualTo(MatchStatus.LIKELY);
        assertThat(match.confidence()).isEqualTo(85);
        assertThat(match.reason()).contains("corroborating identity evidence is unavailable")
                .contains("Manual verification required");
    }

    @Test
    void keepsExactDomainWithoutExactNameAsLikely() {
        Company company = company(7, "Acme Technologies", "acme.example", List.of());
        assertThat(matcher.match(candidate("Different Legal Name", "https://acme.example"), List.of(company)).status())
                .isEqualTo(MatchStatus.LIKELY);
    }

    @Test
    void neverConfirmsFuzzyOnlyNames() {
        Company acme = company(1, "Acme", "acme.example", List.of());
        assertThat(matcher.match(candidate("Acme Labs", null), List.of(acme)).status()).isEqualTo(MatchStatus.LIKELY);
    }

    @Test
    void marksMultipleExactNameMatchesAmbiguous() {
        Company acme = company(1, "Acme", "acme.example", List.of());
        Company duplicate = company(2, "Acme", "other.example", List.of());
        assertThat(matcher.match(candidate("Acme", null), List.of(acme, duplicate)).status())
                .isEqualTo(MatchStatus.AMBIGUOUS);
    }

    @Test
    void leavesUnmatchedIdentityUnmatched() {
        Company acme = company(1, "Acme", "acme.example", List.of());
        assertThat(matcher.match(candidate("Northwind Robotics", null), List.of(acme)).status())
                .isEqualTo(MatchStatus.UNMATCHED);
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
