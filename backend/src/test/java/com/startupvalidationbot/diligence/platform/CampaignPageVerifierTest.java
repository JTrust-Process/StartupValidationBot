package com.startupvalidationbot.diligence.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.startupvalidationbot.diligence.CampaignVerification.*;
import com.startupvalidationbot.diligence.discovery.CampaignUrlLineage;
import com.startupvalidationbot.offering.OfferingDomain.*;

class CampaignPageVerifierTest {
    private static final String URL = "https://wefunder.com/acme";
    private static final Map<String,String> FACTS = Map.of("intermediaryCik", "0001670254",
            "issuerWebsite", "https://acme.example", "_secCampaign.state", "CONFIRMED", "_secCampaign.url", URL,
            "_secCampaign.sourceUrl", "https://www.sec.gov/Archives/primary_doc.xml");
    private static Offering offering(MatchStatus identity) {
        return new Offering(1, 7L, "Acme Energy", "Acme Energy, Inc.", "0001234567", "Wefunder", "Wefunder Portal LLC",
                URL, "https://www.sec.gov/Archives/primary_doc.xml", "accession", "020-1", "C", LocalDate.now(), "REG_CF",
                "SAFE", null, null, null, null, LocalDate.now().plusDays(30), null, Status.ACTIVE, "SEC_EDGAR", identity, 100,
                "fixture", LocalDateTime.now(), LocalDateTime.now());
    }
    private static Observation assess(String html, Map<String,String> facts, MatchStatus identity) {
        Offering offering = offering(identity);
        var page = new WefunderOfferingEnricher(mock(PlatformHttpClient.class)).parse(offering, URI.create(URL), html);
        return CampaignPageVerifier.assess(offering, facts, CampaignUrlLineage.evidence(URL, FACTS, null, "SEC_EDGAR"), page, 2);
    }
    @Test void actualIssuerMatchedPageKeepsPublicExcerptAndTimestamp() {
        var value = assess("<h1>Acme Energy</h1><p>Minimum investment: $100.</p><p>Offering is open for investment.</p>", FACTS, MatchStatus.CONFIRMED);
        assertThat(value.pageState()).isEqualTo(PageState.VERIFIED);
        assertThat(value.publicStatus()).isEqualTo("ACTIVE");
        assertThat(value.statusExcerpt()).contains("open for investment");
        assertThat(value.observedAt()).isNotNull();
        assertThat(value.campaignUrl()).isEqualTo(URL);
        assertThat(value.httpRequests()).isEqualTo(2);
    }
    @Test void reservationIsNotAnOpenOffering() {
        var value = assess("<h1>Acme Energy</h1><p>Accepting reservations only.</p>", FACTS, MatchStatus.CONFIRMED);
        assertThat(value.pageState()).isEqualTo(PageState.VERIFIED);
        assertThat(value.publicStatus()).isEqualTo("RESERVATION");
    }
    @Test void genericCompanyOrPlatformPageIsNotVerified() {
        assertThat(assess("<h1>Acme Energy</h1><p>Welcome to our site.</p>", FACTS, MatchStatus.CONFIRMED).sourceOutcome()).isEqualTo("NOT_A_CAMPAIGN");
        assertThat(assess("<h1>Welcome to Wefunder</h1><nav>Invest now</nav>", FACTS, MatchStatus.CONFIRMED).pageState()).isNotEqualTo(PageState.VERIFIED);
    }
    @Test void botChallengeAndLoginWallCannotPassWithHttpSuccess() {
        for (String html : new String[]{"<title>Just a moment</title><p>Offering is open for investment.</p>", "<h1>Sign in</h1><p>Minimum investment: $100.</p>"})
            assertThat(assess(html, FACTS, MatchStatus.CONFIRMED).pageState()).isEqualTo(PageState.ACCESS_BLOCKED);
    }
    @Test void wrongLegalIssuerAndWrongDomainRemainMismatches() {
        assertThat(assess("<h1>Acme Energy</h1><p>Legal issuer: Unrelated Corporation.</p><p>Minimum investment: $100.</p>", FACTS, MatchStatus.CONFIRMED).pageState()).isEqualTo(PageState.ISSUER_MISMATCH);
        assertThat(assess("<h1>Acme Energy</h1><p>Minimum investment: $100.</p><a href='https://wrong.example'>Company website</a>", FACTS, MatchStatus.CONFIRMED).pageState()).isEqualTo(PageState.ISSUER_MISMATCH);
    }
    @Test void exactNameAloneAndAmbiguousOfferingDoNotVerify() {
        assertThat(assess("<h1>Acme Energy</h1><p>Minimum investment: $100.</p>", Map.of(), MatchStatus.LIKELY).pageState()).isEqualTo(PageState.ISSUER_UNRESOLVED);
        assertThat(assess("<h1>Acme Energy</h1><p>Minimum investment: $100.</p>", FACTS, MatchStatus.AMBIGUOUS).pageState()).isEqualTo(PageState.ISSUER_UNRESOLVED);
    }
    @Test void sourceFailuresNeverBecomeClosedOfferings() {
        var link = CampaignUrlLineage.evidence(URL, FACTS, null, "SEC_EDGAR");
        for (String outcome : new String[]{"HTTP_403", "HTTP_404", "HTTP_429", "TIMEOUT", "ROBOTS_DENIED", "REDIRECT_REJECTED"})
            assertThat(CampaignPageVerifier.failure(offering(MatchStatus.CONFIRMED), link, outcome, "Unavailable public source", 1).publicStatus()).isEqualTo("UNKNOWN");
    }
}
